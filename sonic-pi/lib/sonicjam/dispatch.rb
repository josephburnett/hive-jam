module SonicJam

  class Dispatch

    def initialize(state, resolution)
      @state = state
      @resolution = resolution
    end

    def dispatch(tick)
      root = @state.get_state :root
      _dispatch_grid root, tick
    end

    def _dispatch_grid(grid, tick, parent_track={})
      bpc = grid[:bpc]
      thunks = []
      grid[:tracks].each_index do |index|
        track = grid[:tracks][index]
        type = track[:type]
        beats = track[:beats]
        on, boundary = SonicJam::_on_the_beat?(bpc, @resolution, beats, tick)

        # Apply inheritance from parent track
        synth = track.fetch(:synth, parent_track.fetch(:synth, nil))
        synth_params = track.fetch(:'synth-params', {})
        synth_params = parent_track.fetch(:'synth-params', {}).merge(synth_params)
        sample = track.fetch(:sample, parent_track.fetch(:sample, nil))
        sample_params = track.fetch(:'sample-params', {})
        sample_params = parent_track.fetch(:'sample-params', {}).merge(sample_params)
        fx = track.fetch(:fx, [])
        fx = parent_track.fetch(:fx, []) + fx

        case type
        when "grid"
          if not on
            next
          end
          grid_id = track[:id].to_sym
          sub_grid = @state.get_state(grid_id)
          thunks += _dispatch_grid sub_grid, tick, {
                                    :'synth-params' => synth_params,
                                    :'sample-params' => sample_params,
                                    fx: fx
                                  }
        when "synth"
          if not on and boundary
            next
          end
          thunks.push(lambda { _dispatch_synth synth, synth_params, fx })
        when "sample"
          if not on and boundary
            next
          end
          thunks.push(lambda { _dispatch_sample sample, sample_params, fx })
        else
          # do nothing
        end
      end

      return thunks
    end

    def _dispatch_synth(synth, params, fx)
      # TODO
    end

    def _dispatch_sample(sample, params, fx)
      # TODO
    end
  end

  def self._on_the_beat?(bpc, resolution, beats, tick)
    tpc = Rational(bpc) / Rational(resolution)
    boundary = (tick % tpc.ceil).zero?
    i = (tick / tpc).floor
    on = beats[i % beats.length][0] == 1
    return on, boundary
  end
  
end
