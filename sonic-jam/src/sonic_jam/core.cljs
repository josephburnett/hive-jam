(ns sonic-jam.core
  (:require-macros [cljs.core.async.macros :refer [go-loop]]
                   [cljs.core.async.macros :refer [go]])
  (:require [om.core :as om :include-macros true]
            [om.dom :as dom :include-macros true]
            [cljs.core.async :refer [chan <! >!]]
            [chord.client :refer [ws-ch]]))

(enable-console-print!)

(println "Edits to this text should show up in your developer console.")

;; define your app data so that it doesn't get over-written on reload

(defonce app-state (atom {:text "Hello world!"}))

(defn ping [data owner]
  (reify
    om/IWillMount
    (will-mount [_]
      (go
        (let [{:keys [ws-channel error]} (<! (ws-ch "ws://127.0.0.1:4550/grid"
                                                    {:format :json}))]
          (om/set-state! owner :ws ws-channel)
          (go-loop []
            (let [{:keys [message error] :as msg} (<! ws-channel)]
              (print "Got message:" message)
              (when message
                (recur)))))))
      ;; (let [ws (js/WebSocket. "ws://127.0.0.1:4550/grid")]
      ;;   (doall
      ;;     (map #(aset ws (first %) (second %))
      ;;          [["onopen" #(println "websocket open")]
      ;;           ["onclose" #(println "websocket close")]
      ;;           ["onerror" #(println "websocket error:" %)]
      ;;           ["onmessage" #(println "websocket message:" %)]]))
      ;;   (om/set-state! owner :ws ws)))
    om/IInitState
    (init-state [_] {})
    om/IRenderState
    (render-state [owner state]
      (dom/button
        #js {:onClick #(do
                         (println "Sending /ping")
                         (go (>! (:ws state) "{}"))
                         (println "Sent /ping"))}
	"Ping"))))

(om/root ping app-state
  {:target (. js/document (getElementById "app"))})


(defn on-js-reload []
  ;; optionally touch your app-state to force rerendering depending on
  ;; your application
  ;; (swap! app-state update-in [:__figwheel_counter] inc)
)
