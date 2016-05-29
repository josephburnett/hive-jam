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
      _validate_grid @state[:root]
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
        _validate_grid value
        _check key.equal?(value[:id].to_sym), "Grid must be registered under its id."
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
      _validate_grid state
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

    def _check(predicate, message)
      if not predicate
        raise InvalidState.new(message)
      end
    end

    def _check_not(predicate, message)
      _check (not predicate), message
    end

    def _check_int(value, message)
      begin
        Integer(value)
      rescue
        raise InvalidState.new("#{message} (#{value.to_s})")
      end
    end

    def _check_array(value, message)
      _check value.kind_of?(Array), message
    end
    
    def _check_one_of(value, message, *one_of)
      _check one_of.include?(value), "#{message} (#{value}) (#{one_of})"
    end

    def _check_keys(hash, message, *one_of)
      hash.keys.each do |k|
        _check_one_of k, message, *one_of
      end
    end
    
    def _validate_grid(grid)
      _check_not grid.nil?, "Grid must not be nil."
      _check_not grid[:name].nil?, "Grid name must not be nil."
      _check_not grid[:id].nil?, "Grid id must not be nil."
      _check_not grid[:bpc].nil?, "Grid bpc must not be nil."
      _check_int grid[:bpc], "Grid bpc must be an integer."
      _check_not grid[:tracks].nil?, "Grid tracks must not be nil."
      _check_array grid[:tracks], "Grid tracks must be an array."
      _check_keys grid, "Grid keys must be one of.", :name, :id, :bpc, :tracks
    end

    def _validate_track(track)
      _check_not track.nil?, "Track must not be nil."
      _check_not track[:id], "Track id must not be nil."
      _check_not track[:type], "Track type must not be nil."
      _check_one_of track[:type], "Track type must be one one of.",
                   ["none", "grid", "synth", "sample"]
      _check_not track[:beats].nil?, "Track beats must not be nil."
      _check_array track[:beats], "Track beats must be an array."
      _check_keys track, "Track keys must be one of.", :type, :id, :beats,
                  :params, :fx, :'synth-params', :'sample-params'
    end
    
  end
end
