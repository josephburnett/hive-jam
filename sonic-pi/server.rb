require 'thread'

REBOOT = false

# TIME

bpm = 450

live_loop :beat_loop do
  cue :beat
  sleep 60.0/bpm
end

# EXEC

defonce :exec, override: REBOOT do
  beat = 0
  in_thread do
    loop do
      sync :beat
      beat += 1
      begin
        read_note(beat)
      rescue Exception => e
        puts "exception in :exec:"
        puts e.message
        puts e.backtrace
      end
    end
  end
end

define :read_note do |beat|
  if not defined? _state
    puts "state component missing"
    return
  end
  for ns in _state.keys do
      staff = _state[ns]
      if not staff
        puts "#{ns} missing"
        next
      end
      puts staff
      if not staff.respond_to? :each_with_index
        puts "#{ns} :staff not iterable"
        next
      end
      staff.each_with_index do |bar, i|
        beats = bar[:beats]
        if not beats
          puts "#{ns} :staff #{i} :beats missing"
          next
        end
        if (bools *beats)[beat]
          bar_copy = Marshal.load(Marshal.dump(bar))
          bar_copy.delete(:beats)
          dispatch bar_copy
        end
      end
    end
  end
  
  # DISPATCH
  
  define :dispatch do |note|
    
    if note[:sample]
      s = note[:sample].to_sym
      note.delete(:sample)
      if note[:fx]
        fx = note[:fx]
        note.delete(:fx)
        with_fx fx.to_sym do
          sample s, **note
        end
      else
        sample s, **note
      end
      return
    end
    
    if note[:play]
      p = note[:play]
      note.delete(:play)
      play p, **note
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
    end
  end
  
  define :drop_state do |ns|
    if _ns_ok(ns)
      mutex.synchronize {
        _state.delete(ns)
      }
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
    jam_client.send("/state", client_id, ns, get_state_json(ns))
  end
  
  jam_server.add_method("/set-state") do |args|
    assert(args.length == 3)
    client_id = args[0]
    ns = args[1].intern
    state = JSON.parse(args[2], symbolize_names: true)
    set_state ns, state
    jam_client.send("/state", client_id, ns, get_state_json(ns))
  end
  
  jam_server.add_method("/get-state") do |args|
    assert(args.length == 2)
    client_id = args[0]
    ns = args[1].to_sym
    jam_client.send("/state", client_id, ns, get_state_json(ns))
  end
  