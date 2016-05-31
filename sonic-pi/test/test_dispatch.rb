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
    
  end
end
