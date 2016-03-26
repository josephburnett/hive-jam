# DISPATCH

define :dispatch do |note|

  if note[:sample]
    s = note[:sample]
    note.delete(:sample)
    sample s, **note
    return
  end

  if note[:play]
    p = note[:play]
    note.delete(:play)
    play p, **note
    return
  end

end
