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
  if not defined? state
    puts "state missing"
    return
  end
  staff = state[:staff]
  if not staff
    puts "state[:staff] missing"
    return
  end
  if not staff.respond_to? :each_with_index
    puts "state[:staff] not iterable"
    return
  end
  staff.each_with_index do |bar, i|
    beats = bar[:beats]
    if not beats
      puts "state[:staff][#{i}][:beats] missing"
      return
    end
    if (bools *beats)[beat]
      bar_copy = Marshal.load(Marshal.dump(bar))
      bar_copy.delete(:beats)
      dispatch bar_copy
    end
  end
end
