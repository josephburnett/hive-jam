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
    end

    def drop_state(id)
      @mutex.synchronize {
        @state.delete(id.to_sym)
      }
    end

    def _check(predicate, message)
      if not predicate
        raise InvalidState.new(message)
      end
    end

    def _check_not(predicate, message)
      _check (not predicate), message
    end

    def _check_rational(value, message)
      begin
        Rational(value)
      rescue
        raise InvalidState.new("#{message} (#{value.to_s})")
      end
    end

    def _check_array(value, message)
      _check value.kind_of?(Array), message
    end

    def _check_hash(value, message)
      _check value.kind_of?(Hash), message
    end

    def _check_one_of(value, message, *one_of)
      _check one_of.include?(value), "#{message} #{one_of} (#{value})"
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
      _check_rational grid[:bpc], "Grid bpc must be a rational."
      _check_not grid[:tracks].nil?, "Grid tracks must not be nil."
      _check_array grid[:tracks], "Grid tracks must be an array."
      _check_keys grid, "Grid keys must be one of.", :name, :id, :bpc, :tracks
      grid[:tracks].each do |track|
        _validate_track(track)
      end
      # TODO validate fx
      _validate_acyclic grid, @state
    end

    def _validate_track(track)
      _check_not track.nil?, "Track must not be nil."
      _check_not track[:type].nil?, "Track type must not be nil."
      _check_one_of track[:type], "Track type must be one one of.",
        "none", "grid", "synth", "sample"
      _check_not track[:beats].nil?, "Track beats must not be nil."
      _check_array track[:beats], "Track beats must be an array."
      _check_keys track, "Track keys must be one of.", :type, :beats,
        :fx, :synth, :'synth-params', :sample, :'sample-params',
        :'grid-type', :'grid-id', :id, :on
      if track[:'synth-params']
        _check_hash track[:'synth-params'], "Track synth-params must be a hash."
      end
      if track[:'sample-params']
        _check_hash track[:'sample-params'], "Track sample-params must be a hash."
      end
      if track[:fx]
        fx = track[:fx]
        _check_array fx, "Fx chain must be an array."
        fx.each do |x|
          _check_not x.nil?, "Fx must not be nil."
          _check_hash x, "Fx must be a hash."
        end
      end
      # TODO validate grid id references
    end

    def _validate_acyclic(candidate_grid, state, current_grid=nil, path=nil)
      if path
        _check_not path.include?(current_grid[:id]), "Grid introduces a cycle."
        path = path + [current_grid[:id]]
      else
        path = [candidate_grid[:id]]
        current_grid = candidate_grid
      end
      if not current_grid[:tracks]
        return
      end
      current_grid[:tracks].each do |t|
        if not t[:'grid-id']
          next
        end
        if t[:type] == "grid"
          if t[:'grid-id'] == candidate_grid[:id]
            # Check the state as it would be with the candidate grid.
            next_grid = candidate_grid
          else
            next_grid = state[t[:'grid-id'].to_sym]
          end
          _check_not next_grid.nil?, "Missing grid #{t[:'grid-id']} when validating acyclic."
          _validate_acyclic(candidate_grid, state, next_grid, path)
        end
      end
    end

  end
end
