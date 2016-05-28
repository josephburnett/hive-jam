require "json"
require "thread"

module SonicJam
  class State

    def initialize
      @mutex = Mutex.new
      @state = {
        root: {
          name: "root",
          id: "root",
          bpc: "1",
          tracks: [],
        }
      }
    end

    def save_state(filename)
      @mutex.synchronize {
        open(filename, 'w') do |f|
          f.puts JSON.pretty_generate(@state)
        end
      }
    end

    def load_state(filename)
      state = JSON.parse(File.read(filename), symbolize_names: true)
      # TODO validate state
      @mutex.synchronize {
        @state = state
      }
    end

    def get_state(id)
      @mutex.synchronize {
        return @state[id.to_sym]
      }
    end

    def set_state(id, state)
      # TODO validate state
      @mutex.synchronize {
        @state[id.to_sym] = state
      }
      # TODO broadcast state change?
    end

    def drop_state(id)
      @mutex.synchronize {
        @state.delete(id.to_sym)
      }
      # TODO broadcast state change?
    end
    
  end
end
