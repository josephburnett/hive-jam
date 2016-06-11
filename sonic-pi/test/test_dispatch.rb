require "minitest/autorun"

require_relative "../lib/sonicjam/dispatch"
require_relative "../lib/sonicjam/state"

module SonicJam
  class TestDispatch < Minitest::Unit::TestCase

    def setup
      @state = State.new
      @dispatch = Dispatch.new(@state, "1/32")
    end

    def test_on_the_beat
      beats = [[1], [0], [1]]
      check_on_the_beat(bpc="1", res="1", beats, tick=0, expect_on=true, expect_bound=true, expect_index=0)
      check_on_the_beat(bpc="1", res="1", beats, tick=1, expect_on=false, expect_bound=true, expect_index=1)
      check_on_the_beat(bpc="1", res="1", beats, tick=2, expect_on=true, expect_bound=true, expect_index=2)
      check_on_the_beat(bpc="1", res="1", beats, tick=3, expect_on=true, expect_bound=true, expect_index=0)
      check_on_the_beat(bpc="1", res="1", beats, tick=4, expect_on=false, expect_bound=true, expect_index=1)
      check_on_the_beat(bpc="1", res="1", beats, tick=5, expect_on=true, expect_bound=true, expect_index=2)
      check_on_the_beat(bpc="1", res="1/2", beats, tick=0, expect_on=true, expect_bound=true, expect_index=0)
      check_on_the_beat(bpc="1", res="1/2", beats, tick=1, expect_on=true, expect_bound=false, expect_index=0)
      check_on_the_beat(bpc="1", res="1/2", beats, tick=2, expect_on=false, expect_bound=true, expect_index=1)
      check_on_the_beat(bpc="1", res="1/2", beats, tick=3, expect_on=false, expect_bound=false, expect_index=1)
      check_on_the_beat(bpc="1", res="1/2", beats, tick=4, expect_on=true, expect_bound=true, expect_index=2)
      check_on_the_beat(bpc="1", res="1/2", beats, tick=5, expect_on=true, expect_bound=false, expect_index=2)
      check_on_the_beat(bpc="3", res="1", beats, tick=0, expect_on=true, expect_bound=true, expect_index=0)
      check_on_the_beat(bpc="3", res="1", beats, tick=1, expect_on=true, expect_bound=false, expect_index=0)
      check_on_the_beat(bpc="3", res="1", beats, tick=2, expect_on=true, expect_bound=false, expect_index=0)
      check_on_the_beat(bpc="3", res="1", beats, tick=3, expect_on=false, expect_bound=true, expect_index=1)
      check_on_the_beat(bpc="3", res="1", beats, tick=4, expect_on=false, expect_bound=false, expect_index=1)
      check_on_the_beat(bpc="3", res="1", beats, tick=5, expect_on=false, expect_bound=false, expect_index=1)
      check_on_the_beat(bpc="3", res="1", beats, tick=6, expect_on=true, expect_bound=true, expect_index=2)
      check_on_the_beat(bpc="3", res="1", beats, tick=7, expect_on=true, expect_bound=false, expect_index=2)
      check_on_the_beat(bpc="3", res="1", beats, tick=8, expect_on=true, expect_bound=false, expect_index=2)
      check_on_the_beat(bpc="1", res="1/8", [[1]], tick=0, expect_on=true, expect_bound=true, expect_index=0)
      check_on_the_beat(bpc="1", res="1/8", [[1]], tick=1, expect_on=true, expect_bound=false, expect_index=0)
      check_on_the_beat(bpc="1", res="1/8", [[1]], tick=2, expect_on=true, expect_bound=false, expect_index=0)
      check_on_the_beat(bpc="1", res="1/8", [[1]], tick=3, expect_on=true, expect_bound=false, expect_index=0)
      check_on_the_beat(bpc="1", res="1/8", [[1]], tick=4, expect_on=true, expect_bound=false, expect_index=0)
      check_on_the_beat(bpc="1", res="1/8", [[1]], tick=5, expect_on=true, expect_bound=false, expect_index=0)
      check_on_the_beat(bpc="1", res="1/8", [[1]], tick=6, expect_on=true, expect_bound=false, expect_index=0)
      check_on_the_beat(bpc="1", res="1/8", [[1]], tick=7, expect_on=true, expect_bound=false, expect_index=0)
      check_on_the_beat(bpc="1", res="1/8", [[1]], tick=8, expect_on=true, expect_bound=true, expect_index=0)
    end

    def check_on_the_beat(bpc, res, beats, tick, expect_on, expect_bound, expect_index)
      (on, bound, index) = SonicJam::_on_the_beat?(bpc, res, beats, tick)
      parameters = ("bpc: #{bpc} res: #{res} beats: #{beats} tick: #{tick} " +
                    "expect on: #{expect_on} bound: #{expect_bound} index: #{expect_index}")
      assert_equal expect_on, on, parameters
      assert_equal expect_bound, bound, parameters
      assert_equal expect_index, index, parameters
    end

    def test_dispatch_grid_simple
      @state.set_state({ id: "a", name: "a", bpc: "1",
                         tracks: [
                           { type: "synth", synth: "synth", beats: [[1]] }
                         ]
                       })
      @state.set_state({ id: "root", name: "root", bpc: "1",
                         tracks: [
                           { type: "grid", :'grid-id' => "a", beats: [[1]] }
                         ]
                       })
      check_dispatch_grid_calls [
        { synth: "synth", params: {}, fx: [] }
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
                           { type: "grid", :'grid-id' => "a", beats: [[1]],
                             :'synth-params' => { from: "grid-b-0" },
                             :'sample-params' => { from: "grid-b-0" },}
                         ]
                       })
      @state.set_state({ id: "root", name: "root", bpc: "1",
                         tracks: [
                           { type: "grid", :'grid-id' => "b", beats: [[1]] }
                         ]
                       })
      check_dispatch_grid_calls [
        { synth: "synth-1", params: { from: "grid-b-0" }, fx: [] },
        { synth: "synth-2", params: { from: "grid-a-1" }, fx: [] },
        { sample: "sample-1", params: { from: "grid-b-0" }, fx: [] },
        { sample: "sample-2", params: { from: "grid-a-3" }, fx: [] },
      ]
    end

    def test_dispatch_grid_type_inheritance
      @state.set_state({ id: "a", name: "a", bpc: "1",
                         tracks: [
                           { type: "synth", synth: "synth-1", beats: [[1]] },
                           { type: "sample", sample: "sample-1", beats: [[1]] },
                           { type: "none", beats: [[1]] },
                         ]
                       })
      @state.set_state({ id: "root", name: "root", bpc: "1",
                         tracks: [
                           { type: "grid", :'grid-id' => "a", beats: [[1]],
                             :'grid-type' => "synth", :'synth' => "synth-2" },
                           { type: "grid", :'grid-id' => "a", beats: [[1]],
                             :'grid-type' => "sample", :'sample' => "sample-2" },
                         ]
                       })
      check_dispatch_grid_calls [
        { synth: "synth-1", params: {}, fx: [] },
        { sample: "sample-1", params: {}, fx: [] },
        { synth: "synth-2", params: {}, fx: [] },
        { synth: "synth-1", params: {}, fx: [] },
        { sample: "sample-1", params: {}, fx: [] },
        { sample: "sample-2", params: {}, fx: [] },
      ]
    end

    def test_dispatch_grid_fx_inheritance
      @state.set_state({ id: "a", name: "a", bpc: "1",
                         tracks: [
                           { type: "synth", synth: "synth-1", beats: [[1]] },
                           { type: "synth", synth: "synth-2", beats: [[1]],
                             fx: [{ fx: "fx-1", params: {} }]},
                         ]
                       })
      @state.set_state({ id: "root", name: "root", bpc: "1",
                         tracks: [
                           { type: "grid", :'grid-id' => "a", beats: [[1]],
                             fx: [{ fx: "fx-2", params: {} }]},
                         ]
                       })
      check_dispatch_grid_calls [
        { synth: "synth-1", params: {}, fx: [
            { fx: "fx-2", params: {}}
          ]},
        { synth: "synth-2", params: {}, fx: [
            { fx: "fx-1", params: {} },
            { fx: "fx-2", params: {} },
          ]},
      ]
    end

    def test_dispatch_grid_silence
      @state.set_state({ id: "root", name: "root", bpc: "1",
                         tracks: [
                           { type: "synth", synth: "synth-1", beats: [[0]] }
                         ]
                       })
      check_dispatch_grid_calls []
    end
    
    def check_dispatch_grid_calls(expected)
      dispatches, _ = @dispatch.dispatch(0)
      assert_equal expected, dispatches
    end

    def test_dispatch_grid_indices
      @dispatch = Dispatch.new(@state, "1")
      @state.set_state({ id: "b", name: "b", bpc: "1",
                         tracks: [
                           { type: "synth", synth: "s", beats: [[0]] },
                         ]
                       })
      @state.set_state({ id: "a", name: "a", bpc: "1",
                         tracks: [
                           { type: "synth", synth: "s", beats: [[0], [1], [0], [1]] },
                           { type: "synth", synth: "s", beats: [[0], [1], [0]] },
                           { type: "grid", :'grid-id' => "b", beats: [[1], [0]] },
                         ]
                       })
      @state.set_state({ id: "root", name: "root", bpc: "1",
                         tracks: [
                           { type: "grid", :'grid-id' => "a", beats: [[1]] },
                         ]
                       })
      check_dispatch_grid_indices(tick=0, [[0,
                                            [[0, nil],
                                             [0, nil],
                                             [0,
                                              [[0, nil]]]]]])
      check_dispatch_grid_indices(tick=1, [[0,
                                            [[1, nil],
                                             [1, nil],
                                             [1, nil]]]])
      check_dispatch_grid_indices(tick=2, [[0,
                                            [[2, nil],
                                             [2, nil],
                                             [0,
                                              [[0, nil]]]]]])
      check_dispatch_grid_indices(tick=3, [[0,
                                            [[3, nil],
                                             [0, nil],
                                             [1, nil]]]])
    end

    def check_dispatch_grid_indices(tick, expected)
      _, indices = @dispatch.dispatch(tick)
      assert_equal expected, indices, "tick: #{tick}"
    end
  end
end
