# DISPATCH

define :dispatch do |note|
  
  if note[:sample]
    s = note[:sample].to_sym
    note.delete(:sample)
    if note[:fx]
      fx = note[:fx]
      note.delete(:fx)
      with_fx fx.to_sym do
        sample s, **note
      end
    else
      sample s, **note
    end
    return
  end
  
  if note[:play]
    p = note[:play]
    note.delete(:play)
    play p, **note
    return
  end
  
end