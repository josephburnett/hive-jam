(defproject hive-jam "0.1.0-SNAPSHOT"
  :description "FIXME: write this!"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}

  :min-lein-version "2.6.1"
  
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [org.clojure/clojurescript "1.7.228"]
                 [org.clojure/core.async "0.2.374"
                  :exclusions [org.clojure/tools.reader]]
                 [sablono "0.3.6"]
                 [org.omcljs/om "0.9.0"]
                 [jarohen/chord "0.7.0"]]
  
  :plugins [[lein-figwheel "0.5.2"]
            [lein-cljsbuild "1.1.3" :exclusions [[org.clojure/clojure]]]]

  :source-paths ["src"]

  :clean-targets ^{:protect false} ["resources/public/js/compiled" "target"]

  :cljsbuild {:builds
              [{:id "dev"
                :source-paths ["src"]
                :figwheel {:websocket-host :js-client-host
                           :on-jsload "hive-jam.core/on-js-reload"}

                :compiler {:main hive-jam.core
                           :asset-path "js/compiled/out"
                           :output-to "resources/public/js/compiled/hive_jam.js"
                           :output-dir "resources/public/js/compiled/out"
                           :source-map-timestamp true}}
               {:id "min"
                :source-paths ["src"]
                :compiler {:output-to "resources/public/js/compiled/hive_jam.js"
                           :main hive-jam.core
                           :optimizations :advanced
                           :pretty-print false}}]}

  :figwheel {:server-ip "0.0.0.0"
             :css-dirs ["resources/public/css"]})
