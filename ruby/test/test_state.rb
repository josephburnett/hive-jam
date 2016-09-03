require "minitest/autorun"
require "tempfile"

require_relative "../lib/sonicjam/state"

module SonicJam
  class TestState < Minitest::Unit::TestCase

    def setup
      @state = State.new
      @abc = {
        name: "abc",
        id: "abc",
        bpc: 2,
        tracks: [
          {
            type: "synth",
            beats: [[0]],
            fx: [],
            :'synth-params' => {},
            :'sample-params' => {},
          },
        ],
      }
      @tempfile = Tempfile.new('state')
    end
    
    def test_set_get_state
      assert_nil @state.get_state "abc"
      @state.set_state @abc
      assert_equal @abc, (@state.get_state "abc")
    end

    def test_drop_state
      assert_nil @state.get_state "abc"
      @state.set_state @abc
      assert_equal @abc, (@state.get_state "abc")
      @state.drop_state "abc"
      assert_nil @state.get_state "abc"
    end

    def test_save_load_state
      original_root = Marshal.load(Marshal.dump(@state.get_state "root"))
      assert (not original_root.nil?)
      @state.save_state @tempfile.path
      @state.drop_state "root"
      assert_nil @state.get_state "abc"
      @state.load_state @tempfile.path
      assert_equal original_root, (@state.get_state "root")
    end

    def test_validate_no_extra_keys
      assert_raises(InvalidState) {
        @state._validate_grid @abc.merge({extra_key: ""})
      }
      assert_raises(InvalidState) {
        @state._validate_track @abc[:tracks][0].merge({extra_key: ""})
      }
    end

    def test_validate_acyclic_tree
      state = {
        a: {id: "a",
            tracks: [{type: "grid", :'grid-id' => "b"},
                     {type: "grid", :'grid-id' => "c"}]},
        b: {id: "b",
            tracks: [{type: "grid", :'grid-id' => "d"}]},
        c: {id: "c"},
        d: {id: "d"},
      }
      @state._validate_acyclic(state[:a], state=state)
    end

    def test_validate_acyclic_fail
      assert_raises(InvalidState) {
        state = {
          a: {id: "a",
              tracks: [{type: "grid", :'grid-id' => "b"},
                       {type: "grid", :'grid-id' => "c"}]},
          b: {id: "b",
              tracks: [{type: "grid", :'grid-id' => "d"}]},
          c: {id: "c"},
          d: {id: "d"},
        }
        bad_grid = {id: "d",
                    tracks: [{type: "grid", :'grid-id' => "a"}]}
        @state._validate_acyclic(bad_grid, state=state)
      }
    end

    def test_invalid_grid_reference
      assert_raises(InvalidState) {
        @state.set_state({ id: "root", name: "root", bpc: "1",
                           tracks: [
                             { type: "grid", :'grid-id' => "nonexistent", beats: [[1]] },
                           ]
                         })
      }
    end
    
  end
end
