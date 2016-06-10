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
      dispatches = []
      cursors = []
      grid[:tracks].each_index do |index|
        track = grid[:tracks][index]
        beats = track[:beats]
        on, boundary, beat_index = SonicJam::_on_the_beat?(bpc, @resolution, beats, tick)

        # Apply inheritance from parent track
        type = track.fetch(:type, "none")
        grid_type = track.fetch(:'grid-type', parent_track.fetch(:'grid-type', "none"))
        synth = track.fetch(:synth, parent_track.fetch(:synth, nil))
        synth_params = track.fetch(:'synth-params', {})
        synth_params = parent_track.fetch(:'synth-params', {}).merge(synth_params)
        sample = track.fetch(:sample, parent_track.fetch(:sample, nil))
        sample_params = track.fetch(:'sample-params', {})
        sample_params = parent_track.fetch(:'sample-params', {}).merge(sample_params)
        fx = track.fetch(:fx, []) + parent_track.fetch(:fx, [])

        if type == "none"
          type = grid_type
        end

        case type
        when "grid"
          if not on or not track[:'grid-id']
            cursors.push([beat_index, nil])
            next
          end
          grid_id = track[:'grid-id'].to_sym
          sub_grid = @state.get_state(grid_id)
          sub_dispatches, sub_cursors = _dispatch_grid sub_grid, tick, {
            :'grid-type' => grid_type,
            synth: synth,
            :'synth-params' => synth_params,
            sample: sample,
            :'sample-params' => sample_params,
            fx: fx
          }
          dispatches += sub_dispatches
          cursors.push([beat_index, sub_cursors])
        when "synth"
          cursors.push([beat_index, nil])
          if not (on and boundary)
            next
          end
          dispatches.push({ synth: synth, params: synth_params, fx: fx })
        when "sample"
          cursors.push([beat_index, nil])
          if not (on and boundary)
            next
          end
          dispatches.push({ sample: sample, params: sample_params, fx: fx })
        else
          cursors.push([beat_index, nil])
        end
      end

      return dispatches, cursors

    end
  end

  def self._on_the_beat?(bpc, resolution, beats, tick)
    tpc = Rational(bpc) / Rational(resolution)
    boundary = (tick % tpc.ceil).zero?
    i = (tick / tpc).floor
    index = i % beats.length
    on = beats[index][0] == 1
    return on, boundary, index
  end

end