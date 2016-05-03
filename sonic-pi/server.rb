require 'thread'

# TIME

define :res do Rational('1/8') end

define :verbosity do 1 end

live_loop :main do
  use_bpm 110
  use_cue_logging (verbosity > 1) ? true : false
  use_debug (verbosity > 0) ? true : false
  wait res
  root = _state[:root]
  if root.nil?
    puts "[ERROR] no root state"
    next
  end
  t = tick
  dispatch_grid root, t
end

# DISPATCH

define :dispatch_grid do |grid, t|
  bpc = Rational(grid[:bpc])
  tpc = bpc / res
  boundary = t % tpc.ceil
  i = (t / tpc).ceil
  if verbosity > 2
    puts "[DEBUG] t: #{t} bpc: #{bpc} tpc: #{tpc} boundary: #{boundary} i: #{i}"
  end
  grid[:tracks].each do |track|
    type = track[:type]
    if type.nil?
      puts "[ERROR] missing type #{track}"
      next
    end
    on = (bools *track[:beats].map{|x|x[0]})[i]
    if type == "grid" and on
      id = track[:id].to_sym
      if id.nil?
        puts "[ERROR] missing id #{track}"
        next
      end
      sub_grid = _state[id]
      if sub_grid.nil?
        puts "[ERROR] missing subgrid #{track}"
        next
      end
      dispatch_grid sub_grid, t
    else
      if boundary == 0 and on
        dispatch_track track
      end
    end
  end
end

define :dispatch_track do |track|
  
  if verbosity > 2
    puts "[DEBUG] dispatching track #{track}"
  end
  
  type = track[:type]
  if type.nil?
    puts "[ERROR] no type for track #{track}"
    return
  end
  params = track[:params]
  if params.nil?
    puts "[ERROR] no params for track #{track}"
    return
  end
  
  if type == "sample"
    s = track[:sample].to_sym
    if s.nil?
      puts "[ERROR] no sample for track #{track}"
      return
    end
    fx = track[:fx]
    if not fx.nil?
      with_fx fx.to_sym do
        sample s, **params
      end
    else
      sample s, **params
    end
    return
  end
  
  if type == "play"
    n = track[:note]
    if n.nil?
      puts "[ERROR] no note for track #{track}"
    end
    play n, **params
    return
  end
end

# STATE

define :mutex do
  Mutex.new
end

defonce :_state do
  {}
end

define :set_state do |ns, state|
  if _ns_ok(ns)
    mutex.synchronize {
      _state[ns] = state
    }
    send_state_json("*", ns)
  end
end

define :drop_state do |ns|
  if _ns_ok(ns)
    mutex.synchronize {
      _state.delete(ns)
    }
    send_state_json("*", ns)
  end
end

define :drop_all_state do
  mutex.synchronize {
    _state.clear
  }
end

define :get_state_json do |ns|
  mutex.synchronize {
    return JSON.dump(_state[ns])
  }
end

define :send_state_json do |client_id, ns|
  jam_client.send("/state", JSON.dump([client_id, ns, get_state_json(ns)]))
end

define :_ns_ok do |ns|
  if not ns or not ns.is_a? Symbol
    puts "ns #{ns} is not a symbol"
    return false
  end
  return true
end

# SERVER

defonce :jam_server do
  SonicPi::OSC::UDPServer.new(4559, use_decoder_cache: true)
end

defonce :jam_client do
  SonicPi::OSC::UDPClient.new("127.0.0.1", 4560, use_encoder_cache: true)
end

jam_server.add_method("/drop-state") do |args|
  assert(args.length == 2)
  client_id = args[0]
  ns = args[1].intern
  drop_state ns
end

jam_server.add_method("/set-state") do |args|
  assert(args.length == 3)
  client_id = args[0]
  ns = args[1].intern
  state = JSON.parse(args[2], symbolize_names: true)
  set_state ns, state
end

jam_server.add_method("/get-state") do |args|
  assert(args.length == 2)
  client_id = args[0]
  ns = args[1].to_sym
  send_state_json(client_id, ns)
end

jam_server.add_method("/get-samples") do |args|
  assert(args.length == 1)
  client_id = args[0]
  while not done
    b = a.take(10)
    jam_client.send("/samples", JSON.dump([client_id, JSON.dump(Array.new(b))]))
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
