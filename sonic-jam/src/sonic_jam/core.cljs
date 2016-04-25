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

(defn handle-change [e owner]
  (om/set-state! owner :state (.. e -target -value)))

(defn ping [data owner]
  (reify
    om/IWillMount
    (will-mount [_]
      (go
        (let [{:keys [ws-channel error]} (<! (ws-ch "ws://127.0.0.1:4550/oscbridge"
                                                    {:format :json}))]
          (om/set-state! owner :ws ws-channel)
          (>! ws-channel {:Address "/get-state" :Params ["beat"]})
          (go-loop []
            (let [{:keys [message error] :as msg} (<! ws-channel)
                  address (get message "Address")
                  params (get message "Params")]
              (print "Got message:" message)
              (when (and (= "/state" address)
                         (= "beat" (first params)))
                (om/set-state! owner :state (second params)))
              (when message
                (recur)))))))
    om/IInitState
    (init-state [_] {:state "[]"})
    om/IRenderState
    (render-state [this state]
      (dom/div nil
       (dom/button
        #js {:onClick #(let [ping {:Address "/ping"}]
                         (print "Sending message:" ping)
                         (go (>! (:ws state) ping)))}
	"Ping")
       (dom/textarea
        #js {:type "text" :ref "state" :value (:state state)
             :onChange #(handle-change % owner)})
       (dom/button
        #js {:onClick #(let [update {:Address "/set-state" :Params ["beat" (:state state)]}]
                         (print "Sending message:" update)
                         (go (>! (:ws state) update)))}
        "Update")))))

(om/root ping app-state
  {:target (. js/document (getElementById "app"))})


(defn on-js-reload []
  ;; optionally touch your app-state to force rerendering depending on
  ;; your application
  ;; (swap! app-state update-in [:__figwheel_counter] inc)
)
