require "json"
require "thread"

module SonicJam

  class InvalidState < ArgumentError ; end
  
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
      state.each do |key, value|
        validate_grid value
        check key.equal?(value[:id].to_sym), "Grid must be registered under its id."
      end
      @mutex.synchronize {
        @state = state
      }
    end

    def get_state(id)
      @mutex.synchronize {
        return @state[id.to_sym]
      }
    end

    def set_state(state)
      validate_grid state
      id = state[:id].to_sym
      @mutex.synchronize {
        @state[id] = state
      }
      # TODO broadcast state change?
    end

    def drop_state(id)
      @mutex.synchronize {
        @state.delete(id.to_sym)
      }
      # TODO broadcast state change?
    end

    private

    def check(predicate, message)
      if not predicate
        raise InvalidState.new(message)
      end
    end

    def check_not(predicate, message)
      if predicate
        raise InvalidState.new(message)
      end
    end
    
    def validate_grid(grid)
      check_not grid.nil?, "Grid must not be nil."
      check_not grid[:name].nil?, "Grid name must not be nil."
      check_not grid[:id].nil?, "Grid id must not be nil."
      # TODO validate tracks
    end

  end
end
