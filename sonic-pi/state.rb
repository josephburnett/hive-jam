# STATE

require 'thread'

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