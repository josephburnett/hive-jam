Sonic Jam is a loop-oriented framework for playing music with [Sonic Pi](http://sonic-pi.net/).

(picture goes here)

# Quick Start

1. Start [Sonic Pi](http://sonic-pi.net/).
2. Download and run Sonic Jam ([linux](https://github.com/josephburnett/sonic-jam/blob/v0.1/release/sonic-jam-linux)) ([osx](https://github.com/josephburnett/sonic-jam/blob/v0.1/release/sonic-jam-osx)).
3. Open http://localhost:8080

# How to Jam

Click on a cell to toggle it on (`1`) and off (`0`).

(picture of a cell in the on position)

Click on the properties icon `{..}` at the end of a track to add a synth or sample.  It will be played when the cursor is on a cell which is on.

(picture of a properties selecting a sample)

Click on the track builder `[+ 8]` to add new tracks.  Pro tip: click on the `+` to show more options; click on the `8` to add another track of the same length.

(picture of track-builder)

Click on the properties icon `{..}` at the top of a grid to change the beats-per-sample (`bpc`) and to change the grid width (`+/-`).

(picture of grid properties expanded)

You can add parameters (`params`) to a synth or sample in the track properties.  Look at the Sonic Pi help pages for each synth or sample to see supported parameters.

(picture of an open params)

You can add effects (`fx`) to a track in the track properties.  Effects are applied in the order in which they are added.  You can parameterize effects, just like synths and samples.  See Sonic Pi help pages for supported parameters.

(picture of open fx)

## Sub-grids

In addition to synth and sample tracks, you can create grid tracks which contain an entirely separate sub-grid.  The sub-grid will be played when the cursor is on a cell which is on.

(picture of a subgrid which is on)

Sub-grids have their own tracks, parameters and effects.  However the tracks in a sub-grid can inherit some default values, parameters and effects from their containing (parent) track.  For example, a parent grid track which has a sub-grid type (`grid-type`) of `sample` will cause all tracks in the sub-grid to be interpreted as sample tracks.  Parameters which are set on the parent track, such as `amp`, will apply to all the sub-grid tracks (unless they have an `amp` parameter of their own.)

(picture of a parameterized grid track playing twinkle twinkle little start)

## Lambdas

Parameters are what make Sonic Pi synths and samples interesting to play with.  In addition to providing a scalar value, you can provide a function (lambda) which will be evaluated each time the track is played.  Any parameter which starts with the `\` character is interpreted as a lambda.

The lambda could do anything from returning a random value to increasing its value over time.  The evaluation context is the same as the Sonic Pi editor except that two additional values are in scope: 1) `beat_index` which is the zero-based index at which the cursor (beat) is in the track and 2) `track_index` which is the zero-based index at which the track is in the grid.  `beat_index` can be used to change the parameters of a track over time, restarting at the end of each loop.  `track_index` can be used to implement something like a piano roll (see Patterns below.)

(picture of a lambda parameter)

# Patterns

## The piano roll

When building a melody it is useful to have the tracks of a grid represent the notes of a scale.  This can be done by parameterizing a parent track with a lambda which calculates `pitch` based on `track_index`.

(example of a piano roll)

## Sub-grid organization

Sub-grids can be used to organize large loops into multiple, independent components.  Or to switch pieces on and off.

(example of a two-part loop)

## Multiplayer jamming

Sub-grids can also be used to give multiple players their own grid to work in.  The player's sub-grids can be played together or in a call-and-response configuration.

(example of a parallel sub-grid setup)

(example of a call-and-response sub-grid setup)

# Architecture

## Data model and state

## Components
