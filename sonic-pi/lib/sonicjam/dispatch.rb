module SonicJam

  class Dispatch

    def initialize(state, resolution)
      @state = state
      @resolution = resolution
    end

    def dispatch(tick)
      root = state.get_state :root
      _dispatch_grid tick, root
    end

    def _dispatch_grid(tick, grid, options={})
      pass
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
