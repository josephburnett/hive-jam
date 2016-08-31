# SERVER

defonce :jam_server do
  SonicPi::OSC::UDPServer.new(_sj_config[:SpBridgePortClient], use_decoder_cache: true)
end

defonce :jam_client do
  SonicPi::OSC::UDPClient.new(_sj_config[:SpBridgePortIp], _sj_config[:SpBridgePortServer], use_encoder_cache: true)
end

# TIME

define :res do Rational(_sj_config[:Resolution]) end

define :verbosity do
  if _sj_config[:Debug]
    return 2
  elsif _sj_config[:Verbose]
    return 1
  else
    return 0
  end
end

live_loop :main do
  use_bpm 120
  use_cue_logging (verbosity > 1) ? true : false
  use_debug (verbosity > 0) ? true : false
  wait res
  t = tick
  begin
    dispatches, cursors = _dispatch.dispatch t
    jam_client.send("/cursors", JSON.dump(["*", JSON.dump(cursors)]))
    dispatches.each do |d|
      if verbosity > 1
        _send_console JSON.dump(d)
      end
      if d[:sample]
        apply_fx d[:fx], lambda { sample d[:sample].to_sym, **d[:params] }
      elsif d[:synth]
        apply_fx d[:fx], lambda { synth d[:synth].to_sym, **d[:params] }
      end
    end
  rescue Exception => e
    _send_error e
  end
end

define :_send_error do |e|
  errors = [e.message] + e.backtrace
  jam_client.send("/errors", JSON.dump(["*", JSON.dump(errors)]))
end

define :_send_console do |msg|
  jam_client.send("/console", JSON.dump(["*", JSON.dump([msg])]))
end

# STATE

defonce :_state do
  SonicJam::State.new
end

define :_send_state do |client_id, ns|
  ns = ns.to_sym
  json = JSON.dump(_state.get_state(ns))
  jam_client.send("/state", JSON.dump([client_id, ns, json]))
end

# DISPATCH

define :_dispatch do
  SonicJam::Dispatch.new(_state, res)
end

define :apply_fx do |fx_chain, thunk|
  if fx_chain.length == 0
    thunk.call()
  else
    fx = fx_chain[0]
    fx_chain = fx_chain[1..-1]
    with_fx fx[:fx], **fx[:params] do
      apply_fx fx_chain, thunk
    end
  end
end

# SERVER METHODS

jam_server.add_method("/drop-state") do |args|
  assert(args.length == 2)
  begin
    client_id = args[0]
    drop_state args[1]
  rescue Exception => e
    _send_error e
  end
end

jam_server.add_method("/set-state") do |args|
  assert(args.length == 3)
  begin
    client_id = args[0]
    ns = args[1].to_sym
    state = JSON.parse(args[2], symbolize_names: true)
    set_state state
  rescue Exception => e
    _send_error e
  end
end

jam_server.add_method("/get-state") do |args|
  assert(args.length == 2)
  begin
    client_id = args[0]
    ns = args[1].to_sym
    _send_state(client_id, ns)
  rescue Exception => e
    _send_error e
  end
end

jam_server.add_method("/get-samples") do |args|
  assert(args.length == 1)
  client_id = args[0]
  a = all_sample_names
  done = false
  while not done
    b = a.take(10)
    jam_client.send("/samples", JSON.dump([client_id, JSON.dump(Array.new(b))]))
    a = a.drop(10)
    if a.count == 0
      done = true
    end
  end
end

jam_server.add_method("/get-synths") do |args|
  assert(args.length == 1)
  client_id = args[0]
  a = synth_names
  done = false
  while not done
    b = a.take(10)
    jam_client.send("/synths", JSON.dump([client_id, JSON.dump(Array.new(b))]))
    a = a.drop(10)
    if a.count == 0
      done = true
    end
  end
end

jam_server.add_method("/ping") do |args|
  assert(args.length == 1)
  client_id = args[0]
  jam_client.send("/pong", JSON.dump([client_id]))
end

jam_server.add_method("/save-state") do |args|
  assert(args.length == 1)
  begin
    client_id = args[0]
    filename = _sj_config[:StateFile]
    if filename.nil? and filename.empty?
      save_state filename
      jam_client.send("/message", JSON.dump([client_id, "State saved."]))
    end
  rescue Exception => e
    _send_error e
  end
end

jam_server.add_method("/load-state") do |args|
  assert(args.length == 1)
  begin
    client_id = args[0]
    filename = _sj_config[:StateFile]
    if not filename.nil? and not filename.empty?
      load_state filename
      jam_client.send("/message", JSON.dump([client_id, "State loaded."]))
    end
  rescue Exception => e
    _send_error e
  end
end


# USER STATE FUNCTIONS

define :save_state do |filename|
  _state.save_state filename
end

define :load_state do |filename|
  _state.load_state filename
  # TODO broadcast all state
end

define :get_state do |ns|
  ns = ns.to_sym
  _state.get_state ns
end

define :set_state do |state|
  _state.set_state state
  _send_state("*", state[:id])
end

define :drop_state do |ns|
  ns = ns.to_sym
  _state.drop_state ns
  _send_state("*", ns)
end

sleep(2)
jam_client.send("/boot-complete", "true")

