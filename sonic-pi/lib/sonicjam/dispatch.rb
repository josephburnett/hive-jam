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
        track_on = track[:on]
        beats = track[:beats]
        on, boundary, beat_index = SonicJam::_on_the_beat?(bpc, @resolution, beats, tick)

        # Apply inheritance from parent track
        type = track.fetch(:type, "none")
        grid_type = track.fetch(:'grid-type', parent_track.fetch(:'grid-type', "none"))
        if type == "none"
          type = grid_type
        end

        synth = track.fetch(:synth, parent_track.fetch(:synth, nil))
        sample = track.fetch(:sample, parent_track.fetch(:sample, nil))

        fx = track.fetch(:fx, []) + parent_track.fetch(:fx, [])

        parent_synth_params = parent_track.fetch(:'synth-params', nil)
        synth_params = Params.new(track.fetch(:'synth-params', {}), parent_synth_params)
        parent_sample_params = parent_track.fetch(:'sample-params', nil)
        sample_params = Params.new(track.fetch(:'sample-params', {}), parent_sample_params)

        case type
        when "grid"
          if not track_on or not on or not track[:'grid-id']
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
          if not track_on or not (on and boundary)
            next
          end
          bind = _get_binding(index, beat_index)
          params = synth_params.materialize(bind)
          sustain = SonicJam::_calculate_sustain(beats[beat_index][0], params, default=0, bpc=bpc)
          params[:sustain] = sustain
          dispatches.push({ synth: synth, params: params, fx: fx })
        when "sample"
          cursors.push([beat_index, nil])
          if not track_on or not (on and boundary)
            next
          end
          bind = _get_binding(index, beat_index)
          params = sample_params.materialize(bind)
          sustain = SonicJam::_calculate_sustain(beats[beat_index][0], params, default=-1, bpc=bpc)
          params[:sustain] = sustain
          dispatches.push({ sample: sample, params: params, fx: fx })
        else
          cursors.push([beat_index, nil])
        end
      end

      return dispatches, cursors

    end

    def _get_binding(row_index, beat_index)
      binding
    end

  end

  def self._calculate_sustain(width, params, default=0, bpc=1)
    sustain = Rational(params.fetch(:sustain, default))
    if sustain == -1
      # Unlimited sustain overrides cell width
      return sustain
    end
    sustain += width - 1
    return sustain * Rational(bpc)
  end

  def self._on_the_beat?(bpc, resolution, beats, tick)
    tpc = Rational(bpc) / Rational(resolution)
    boundary = (tick % tpc.ceil).zero?
    i = (tick / tpc).floor
    index = i % beats.length
    on = beats[index][0] > 0
    return on, boundary, index
  end

end
