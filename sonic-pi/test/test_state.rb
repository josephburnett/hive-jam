require 'minitest/autorun'

module SonicJam
  class TestState < Minitest::Unit::TestCase

    def test_nothing
      puts "hello world"
      assert_equal(1, 1)
    end

  end
end
