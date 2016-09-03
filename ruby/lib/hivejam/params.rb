module HiveJam

  class Params

    def initialize(hash, parent=nil)
      @hash = hash
      @parent = parent
      @keys = Set.new(@hash.keys)
      if not @parent.nil?
        @keys.merge(@parent._keys)
      end
    end

    def materialize(bind)
      materialized_params = {}
      @keys.each do |key|
        materialized_params[key] = _materialize_key(bind, key)
      end
      return materialized_params
    end

    def _materialize_key(bind, key, child=nil)
      bind = _local_variable_set(bind, :child, child)
      value = @hash.fetch(key, child)
      if not value.nil? and value.is_a? String
        if value.start_with?("!")
          # '!' breaks the inheritance chain
          return value[1..-1]
        end
        if value.start_with?("\\")
          value = eval value[1..-1], bind
        end
      end
      if not @parent.nil?
        value = @parent._materialize_key(bind, key, value)
      end
      return value
    end
    
    def _keys
      return @keys
    end

    # TODO: Replace with binding.local_variable_set, available in Ruby 2.2
    def _local_variable_set(bind, variable, value)
      if value
        bind = bind.eval("#{variable.to_s} = \"#{value}\"; binding")
      end
      return bind
    end
    
  end
end
