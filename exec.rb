# EXEC

defonce :exec do
  beat = 0
  in_thread do
    loop do
      sync :beat
      beat += 1
      read_note(beat)
    end
  end
end

define :read_note do |beat|
  for bar in state[:staff]
    if (bools *bar[:beats])[beat]
      sample bar[:sample]
    end
  end
end
