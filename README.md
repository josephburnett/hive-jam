# Hive Jam

Hive Jam is a loop-oriented, multi-player framework for making music with [Sonic Pi](http://sonic-pi.net/).  Players turn on and off grid cells representing synths or samples.  A cursor sweeps with the beat across the grid, playing any cells that are on.  Rows (tracks) can be parameterized to change the sound of a synth or sample.

Tracks can also represent sub-grids which contain their own tracks and parameters.  A sub-grid will be activated when the parent track is on.  Sub-grids can contain their own sub-grids and so forth.

#### This is what it looks like

![Hive Jam](doc/hive-jam.png)

# Quick Start

### Locally
1. Install and run [Sonic Pi](http://sonic-pi.net/).
2. Download and run the [latest Hive Jam](https://github.com/josephburnett/hive-jam/releases/tag/latest) binary.
3. Open [http://localhost:8080](http://localhost:8080)

### In the cloud
From a [Google Cloud Engine](https://cloud.google.com/compute/docs/images) instance:

1. Install docker: `sudo apt-get install -y docker.io`
2. Run Hive Jam: `sudo docker run -d -p 8080:8080 -p 8000:8000 -p 4550:4550 josephburnett/hive-jam`
3. Open the UI webserver at `EXTERNAL_IP:8080`

# How to Jam

Click on a cell to toggle it on (`1`) and off (`0`).

![Turning on a cell](doc/how-to-jam-cell-on.png)

Click on the properties icon `{..}` at the end of a track to add a synth or sample.  It will be played when the cursor is on a cell which is on.

![Selecting a sample](doc/how-to-jam-select-sample.png)

Click on the track builder `[+ 8]` to add new tracks.  Pro tip: click on the `+` to show more options; click on the `8` to add another track of the same length.

![Add a track](doc/how-to-jam-add-track.png)

Click on the properties icon `{..}` at the top of a grid to change the beats-per-sample (`bpc`) and to change the grid width (`+/-`).

![Changing grid properties](doc/how-to-jam-grid-properties.png)

You can add parameters (`params`) to a synth or sample in the track properties.  Look at the Sonic Pi help pages for each synth or sample to see supported parameters.

![Adding track params](doc/how-to-jam-add-params.png)

You can add effects (`fx`) to a track in the track properties.  Effects are applied in the order in which they are added.  You can parameterize effects, just like synths and samples.  See Sonic Pi help pages for supported parameters.

![Adding fx and fx params](doc/how-to-jam-add-fx.png)

## Sub-grids

In addition to synth and sample tracks, you can create grid tracks which contain an entirely separate sub-grid.  The sub-grid will be played when the cursor is at a cell which is on.

![A sub-grid](doc/how-to-jam-sub-grid.png)

Sub-grids tracks have their own parameters and effects.  However the tracks in a sub-grid can inherit some parameters and effects from their containing (parent) track.  For example, a parent track which has a sub-grid type (`grid-type`) of `sample` will cause all tracks in the sub-grid to be interpreted as sample tracks.  Parameters which are set on the parent track, such as `pitch`, will apply to all the sub-grid tracks (unless they have an `pitch` parameter of their own.)

## Lambdas

Parameters are what make Sonic Pi synths and samples interesting to play with.  In addition to providing a scalar value, you can provide a function (lambda) which will be evaluated each time the track is played.  Any parameter which starts with the `\` character is interpreted as a lambda.

The lambda could do anything from returning a random value to increasing its value over time.  The evaluation context is the same as the Sonic Pi editor except that two additional values are in scope: 1) `beat_index` which is the zero-based index at which the cursor (beat) is in the track and 2) `row_index` which is the zero-based index at which the track is in the grid.  `beat_index` can be used to change the parameters of a track over time, restarting at the end of each loop.  `row_index` can be used to implement something like a piano roll (see Patterns below.)

### Twinkle, Twinkle Little Star

#### Organized into two parts

![Twinkle, twinkle little star](doc/how-to-jam-complex-sub-grid-1.png)

#### Part 1

![Twinkle, twinkle little star](doc/how-to-jam-complex-sub-grid-2.png)

#### Part 2

![Twinkle, twinkle little star](doc/how-to-jam-complex-sub-grid-3.png)

Pro tip: right-click on a cell to make a synth sustain (`2`, `3`, ...)

# Patterns

## The piano roll

When building a melody it is useful to have the tracks of a grid represent the notes of a scale.  This can be done by parameterizing a parent track with a lambda which calculates `pitch` based on `row_index`.

![A piano roll](doc/patterns-piano-roll.png)

## Multiplayer jamming

Hive Jam is built from the ground-up to support multiple players.  For example, two players can jam on the same instance by visiting the same URL in their browser.  Changes to one will immediately take effect in the other.

Sub-grids can be used to give each player their own space to work in.  The players can take turns (call-and-response) or play together.

#### Together

![Multiplayer subgrids together](doc/patterns-multiplayer-together.png)

#### Call and response

![Multiplayer subgrids call and response](doc/patterns-multiplayer-call-response.png)

# Architecture

Hive Jam is a framework for controlling Sonic Pi.  It uses a single live-loop to sweep through a data structure, dispatching parameterized calls to the Sonic Pi functions `synth` and `sample` on the beat.

## Data model and state

Server state is kept in the Sonic Pi variable `_state` which is a [`HiveJam::State`](https://github.com/josephburnett/hive-jam/blob/master/sonic-pi/lib/hivejam/state.rb) instance.  The granularity of state manipulation is a single grid through the `set_state` and `get_state` methods.

```
{
  root: {
    bpc: 1,
    tracks: [
      {
        type: "sample",
        sample: "bass_drop_c",
        beats: [[1], [1], [1], [1]]
      },
      {
        type: "grid",
        grid-id: "abcd",
        beats: [[1]]
    ]
  },
  abcd: {
    bpc: 1,
    tracks: [
      {
        type: "synth",
        synth: "fm",
        synth-params: {
          pitch: 20,
        }
        beats: [[1], [0], [0], [0]]
      }
    ]
  }
}
```

In the main live-loop a [`HiveJam::Dispatch`](https://github.com/josephburnett/hive-jam/blob/master/sonic-pi/lib/hivejam/dispatch.rb) instance traverses the state, generating a list of materialized dispatch structures.  Dispatch is responsible for determining which tracks and sub-grids are on/off, implementing sub-grid inheritance and running lambdas to generate scalar parameter values.

#### Beat 0

```
[
  { sample: "base_drop_c" },
  { synth: "fm", params: { pitch: 20 }}
]
```

#### Beat 1

```
[
  { sample: "base_drop_c" }
]
```

#### Beat 4

```
[
  { sample: "base_drop_c" },
  { synth: "fm", params: { pitch: 20 }}
]
```

## Components

```

+----+                    (audio)
| UI |<---------------------------------------------------------+
+----+                                                          |
   ^                                                            |
   |  (state)                                                   |
   V                                                            |
+--------+    +---------------------+                           |
| Hive   |    | +----------+        |    +---------------+      |
| Server |<---->| Dispatch |  Sonic |--->| Supercollider |      |
+--------+    | | Server   |  Pi    |    | Synthesizer   |      |
              | +----------+        |    +---------------+      |
              +---------------------+            |              |
                                                 V              |
                                             +------+           |
                                             | Jack |           |
                                             +------+           |
                                                 |              |
                                                 V              |
                                            +---------+         |
                                            | Darkice |         |
                                            +---------+         |
                                                 |              |
                                                 V              |
                                            +---------+         |
                                            | Icecast |---------+
                                            +---------+
```

The [Dispatch Server](https://github.com/josephburnett/hive-jam/tree/master/ruby) (`ruby/server.rb`) is bootstrapped into Sonic Pi through a series OSC messages to `/run-code` on port 4557.  It starts an OSC server on port 4560.

The [Hive Server](https://github.com/josephburnett/hive-jam/tree/master/golang/src/hivejam) (`golang/server/server.go`) starts an OSC server on port 4559 and a websocket server on 4550.  It multiplexes messages from multiple clients to the Dispatch Server.

The [UI](https://github.com/josephburnett/hive-jam/tree/master/cljs) (`cljs/src/core.cljs`) communicates with the Hive Server over a websocket connection.  It requests synth and sample lists, transmits and receives state changes, and receives a stream of cursor updates (current location of the beat).  Supercollider outputs its audio to Jack.  Darkice feeds it into Icecast which streams the audio back to the UI.

## Development

The typical development workflow is to build Hive Jam from head and launch it.  Then start Figwheel for interactive, live UI coding.  Any change to files in `cljs` will take effect immediately--even without reloading the browser.  Any changes to `golang` or `ruby` require a rebuild (`bin/build`).

### Remote (Google Compute Engine)

1. Launch an Ubuntu 16.04 instance
2. Open ports 3449, 4550, 8000 and 8080 to TCP connections (Networking)
3. `$ bin/deps-ubuntu` (configure Icecast2 with random password--it will be overwritten)
4. `$ bin/build`
5. `$ sudo bin/with-gce-env launch`
6. `$ bin/with-gce-env figwheel`
7. Navigate to http://$EXTERNAL_IP:3449

### Local Ubuntu 16.04

1. `$ bin/deps-ubuntu`
2. `$ bin/build`
3. `$ sonic-pi`
4. `$ build/hive-jam`
5. `$ bin/with-local-env figwheel`
6. Navigate to http://localhost:3449

### Local Ubuntu 16.04 (headless)

1. `$ bin/deps-ubuntu`
2. `$ bin/build`
3. `$ sudo bin/with-local-env launch`
4. `$ bin/with-local-env figwheel`
5. Navigate to http://localhost:3449
 
### Local Raspberry Pi

1. `$ bin/deps-raspberry-pi`
2. `$ bin/build`
3. `$ sonic-pi`
4. `$ build/hive-jam`
5. `$ bin/with-local-env figwheel`
6. Navigate to http://localhost:3449

### Local OS X

1. Install Sonic Pi (http://sonic-pi.net)
2. Install git (type `git` and follow instructions)
3. Install java (type `java` and follow instructions)
4. Install golang (https://golang.org/dl/)
5. `$ bin/build`
6. Start Sonic Pi
7. `$ build/hive-jam`
8. `$ bin/with-local-env figwheel`
9. Navigate to http://localhost:3449

## Bugs

Encountering any problems?  Please report an issue: https://github.com/josephburnett/hive-jam/issues

If possible, please include:

1. What you were doing
2. Any error messages
3. Operating system
4. Sonic Pi version
5. Hive Jam version or commit
