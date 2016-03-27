# EXEC

defonce :exec do
  beat = 0
  in_thread do
    loop do
      sync :beat
      beat += 1
      begin
        read_note(beat)
      rescue Exception => e
        puts "exception in :exec:"
        puts e.message
        puts e.backtrace
      end
    end
  end
end

define :read_note do |beat|
  if not defined? _state
    puts "state component missing"
    return
  end
  for ns in _state.keys do
      staff = _state[ns]
      if not staff
        puts "#{ns} missing"
        next
      end
      puts staff
      if not staff.respond_to? :each_with_index
        puts "#{ns} :staff not iterable"
        next
      end
      staff.each_with_index do |bar, i|
        beats = bar[:beats]
        if not beats
          puts "#{ns} :staff #{i} :beats missing"
          next
        end
        if (bools *beats)[beat]
          bar_copy = Marshal.load(Marshal.dump(bar))
          bar_copy.delete(:beats)
          dispatch bar_copy
        end
      end
    end
  end