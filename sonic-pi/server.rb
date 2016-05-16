require 'thread'

# TIME

define :res do Rational('1/8') end

define :verbosity do 1 end

live_loop :main do
  use_bpm 150
  use_cue_logging (verbosity > 1) ? true : false
  use_debug (verbosity > 0) ? true : false
  wait res
  root = _state[:root]
  if root.nil?
    puts "[ERROR] no root state"
    next
  end
  t = tick
  dispatch_grid root, t, {}
end

# DISPATCH

define :dispatch_grid do |grid, t, inherited_params, inherited_type=nil, inherited_type_value=nil|
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
    if type == "grid"
      if not on
        next
      end
      grid_type = grid[:'grid-type']
      id = track[:id].to_sym
      sub_grid = _state[id]
      if grid_type = "synth"
        params = track[:'synth-params']
        params = inherited_params.merge(params)
        grid_synth = track[:'grid-synth']
        dispatch_grid sub_grid, t, params, grid_type, grid_synth
      elsif grid_type = "sample"
        params = track[:'sample-params']
        params = inherited_params.merge(params)
        grid_sample = track[:'grid-sample']
        dispatch_grid sub_grid, t, params, grid_type, grid_sample
      else
        dispatch_grid sub_grid, t, inherted_params
      end
    else
      params = track[:params]
      params = inherited_params.merge(params)
      if boundary == 0 and on
        dispatch_track track, params, inherited_type, inherited_type_value
      end
    end
  end
end

define :dispatch_track do |track, inherited_params, inherited_type=nil, inherited_type_value=nil|

  if verbosity > 2
    puts "[DEBUG] dispatching track #{track}"
  end

  type = track[:type]
  if type.nil?
    puts "[ERROR] no type for track #{track}"
    return
  end

  if type == "none" and inherited_type
    type = inherited_type
  end

  if type == "sample"
    sample = track[:sample]
    if sample.nil?
      if not inherited_type_value
        return
      end
      sample = inherited_type_value
    end
    s = sample.to_sym
    if s.nil?
      puts "[ERROR] no sample for track #{track}"
      return
    end
    fx = track[:fx]
    if not fx.nil?
      with_fx fx.to_sym do
        sample s, **inherited_params
      end
    else
      sample s, **inherited_params
    end
    return
  end

  if type == "play"
    n = track[:note]
    if n.nil?
      return
    end
    play n, **inherited_params
    return
  end

  if type == "synth"
    synth = track[:synth]
    if synth.nil?
      if not inherited_type_value
        return
      end
      synth = inherited_type_value
    end
    s = synth.to_sym
    if s.nil?
      puts "[ERROR] no synth for track #{track}"
      return
    end
    synth s, **inherited_params
    return
  end
end

# STATE

define :mutex do
  Mutex.new
end

defonce :_state do
  {
    root: {
      name: "",
      id: "root",
      bpc: 1,
      tracks: [],
    }
  }
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