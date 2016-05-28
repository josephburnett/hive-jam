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
        tracks: [],
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
    
  end
end
