require "minitest/autorun"

require_relative "../lib/sonicjam/params"

module SonicJam
  class TestParams < Minitest::Unit::TestCase

    def setup
    end

    def test_simple_hash
      params = Params.new({a: 1, b: 2})
      assert_equal({a: 1, b: 2}, params.materialize(binding))
    end

    def test_inherit_scalar
      params = Params.new({a: 1, b: 1}, Params.new({a: 2}))
      assert_equal({a: 2, b: 1}, params.materialize(binding))
    end

    def test_inheritance_break
      params = Params.new({a: "!foo"}, Params.new({a: "bar"}))
      assert_equal({a: "foo"}, params.materialize(binding))
    end

    def test_lambda
      params = Params.new({a: "\\ 1+1"})
      assert_equal({a: 2}, params.materialize(binding))
    end

    def test_lambda_binding
      a = 1
      params = Params.new({a: "\\ a"})
      assert_equal({a: 1}, params.materialize(binding))
    end

    def test_lambda_inheritance
      a = 2
      params = Params.new({a: "\\ a"}, Params.new({a: "\\ 2 * Integer(child)"}))
      assert_equal({a: 4}, params.materialize(binding))
    end

    def test_child_agnostic
      params = Params.new({}, Params.new({a: 1}))
      assert_equal({a: 1}, params.materialize(binding))
    end

    def test_child_passthrough
      params = Params.new({a: 1}, Params.new({}))
      assert_equal({a: 1}, params.materialize(binding))
    end
    
  end
end
