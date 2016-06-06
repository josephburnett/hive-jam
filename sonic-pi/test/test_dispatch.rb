require "minitest/autorun"

require_relative "../lib/sonicjam/dispatch"

module SonicJam
  class TestDispatch < Minitest::Unit::TestCase

    def setup
      @state = State.new
      @dispatch = Dispatch.new(@state, "1/32")
    end

    def test_on_the_beat
      beats = [[1], [0], [1]]
      check_on_the_beat(bpc="1", res="1", beats, tick=0, expect_on=true, expect_bound=true)
      check_on_the_beat(bpc="1", res="1", beats, tick=1, expect_on=false, expect_bound=true)
      check_on_the_beat(bpc="1", res="1", beats, tick=2, expect_on=true, expect_bound=true)
      check_on_the_beat(bpc="1", res="1", beats, tick=3, expect_on=true, expect_bound=true)
      check_on_the_beat(bpc="1", res="1", beats, tick=4, expect_on=false, expect_bound=true)
      check_on_the_beat(bpc="1", res="1", beats, tick=5, expect_on=true, expect_bound=true)
      check_on_the_beat(bpc="1", res="1/2", beats, tick=0, expect_on=true, expect_bound=true)
      check_on_the_beat(bpc="1", res="1/2", beats, tick=1, expect_on=true, expect_bound=false)
      check_on_the_beat(bpc="1", res="1/2", beats, tick=2, expect_on=false, expect_bound=true)
      check_on_the_beat(bpc="1", res="1/2", beats, tick=3, expect_on=false, expect_bound=false)
      check_on_the_beat(bpc="1", res="1/2", beats, tick=4, expect_on=true, expect_bound=true)
      check_on_the_beat(bpc="1", res="1/2", beats, tick=5, expect_on=true, expect_bound=false)
      check_on_the_beat(bpc="3", res="1", beats, tick=0, expect_on=true, expect_bound=true)
      check_on_the_beat(bpc="3", res="1", beats, tick=1, expect_on=true, expect_bound=false)
      check_on_the_beat(bpc="3", res="1", beats, tick=2, expect_on=true, expect_bound=false)
      check_on_the_beat(bpc="3", res="1", beats, tick=3, expect_on=false, expect_bound=true)
      check_on_the_beat(bpc="3", res="1", beats, tick=4, expect_on=false, expect_bound=false)
      check_on_the_beat(bpc="3", res="1", beats, tick=5, expect_on=false, expect_bound=false)
      check_on_the_beat(bpc="3", res="1", beats, tick=6, expect_on=true, expect_bound=true)
      check_on_the_beat(bpc="3", res="1", beats, tick=7, expect_on=true, expect_bound=false)
      check_on_the_beat(bpc="3", res="1", beats, tick=8, expect_on=true, expect_bound=false)
    end

    def check_on_the_beat(bpc, res, beats, tick, expect_on, expect_bound)
      (on, bound) = SonicJam::_on_the_beat?(bpc, res, beats, tick)
      assert_equal expect_on, on,
                   "Expected on #{expect_on} but got #{on} for #{bpc}, #{res}, #{beats} at #{tick}"
      assert_equal expect_bound, bound,
                   "Expected bound #{expect_bound} but got #{bound} for #{bpc}, #{res}, #{beats} at #{tick}"
    end

    def test_dispatch_grid_simple
      @state.set_state({ id: "a", name: "a", bpc: "1",
                         tracks: [
                           { type: "synth", synth: "synth", beats: [[1]] }
                         ]
                       })
      @state.set_state({ id: "root", name: "root", bpc: "1",
                         tracks: [
                           { type: "grid", id: "a", beats: [[1]] }
                         ]
                       })
      check_dispatch_grid [
        {
          synth: "synth",
          params: {},
          fx: [],
        }
      ]
    end

    def test_dispatch_grid_param_inheritance
      @state.set_state({ id: "a", name: "a", bpc: "1",
                         tracks: [
                           { type: "synth", synth: "synth-1", beats: [[1]] },
                           { type: "synth", synth: "synth-2", beats: [[1]],
                             :'synth-params' => { from: "grid-a-1" }},
                           { type: "sample", sample: "sample-1", beats: [[1]] },
                           { type: "sample", sample: "sample-2", beats: [[1]],
                             :'sample-params' => { from: "grid-a-3" }},
                         ]
                       })
      @state.set_state({ id: "b", name: "b", bpc: "1",
                         tracks: [
                           { type: "grid", id: "a", beats: [[1]],
                             :'synth-params' => { from: "grid-b-0" },
                             :'sample-params' => { from: "grid-b-0" },}
                         ]
                       })
      @state.set_state({ id: "root", name: "root", bpc: "1",
                         tracks: [
                           { type: "grid", id: "b", beats: [[1]] }
                         ]
                       })
      check_dispatch_grid [
        {
          synth: "synth-1",
          params: { from: "grid-b-0" },
          fx: [],
        },
        {
          synth: "synth-2",
          params: { from: "grid-a-1" },
          fx: [],
        },
        {
          sample: "sample-1",
          params: { from: "grid-b-0" },
          fx: [],
        },
        {
          sample: "sample-2",
          params: { from: "grid-a-3" },
          fx: [],
        }
      ]
    end
    
    def check_dispatch_grid(expected)
      @dispatch.instance_variable_set(:@calls, [])
      def @dispatch._dispatch_synth(synth, params, fx)
        @calls.push({ synth: synth, params: params, fx: fx })
      end
      def @dispatch._dispatch_sample(sample, params, fx)
        @calls.push({ sample: sample, params: params, fx: fx })
      end
      thunks = @dispatch.dispatch(0)
      thunks.each do |thunk|
        thunk.call()
      end
      assert_equal expected, @dispatch.instance_variable_get(:@calls)
    end
    
  end
end
