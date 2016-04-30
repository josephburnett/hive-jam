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

(defonce app-state (atom {:grids {:test {}}}))

(defn cell-view [cursor _]
  (reify
    om/IRender
    (render [_]
      (print "rendering cell" cursor)
      (dom/p nil
             (str " " cursor)))))

(defn track-view [cursor _]
  (reify
    om/IRender
    (render [_]
      (apply dom/ol nil
              (om/build-all cell-view (get cursor "beats"))))))

(defn grid-view [cursor _]
  (reify
    om/IRender
    (render [_]
      (dom/div nil
               (dom/h2 nil (get cursor "id"))
               (apply dom/ul nil
                      (om/build-all track-view (get cursor "tracks")))))))

(defn app-view [cursor _]
  (reify
    om/IInitState
    (init-state [_]
      (go
        (let [{:keys [ws-channel error]} (<! (ws-ch "ws://127.0.0.1:4550/oscbridge"
                                                    {:format :json}))]
          (>! ws-channel {:Address "/get-state" :Params ["root"]})
          (go-loop []
            (let [{:keys [message error] :as msg} (<! ws-channel)
                  address (get message "Address")
                  params (get message "Params")]
              (print "Got message:" message)
              (when (= "/state" address)
                (let [grid (js->clj (js/JSON.parse (second params)))]
                  (om/update! cursor [:grids (first params)] grid)))
              (when message
                (recur))))))
      {})
    om/IRenderState
    (render-state [_ state]      
      (apply dom/div nil
             (om/build-all grid-view (vals (:grids cursor)))))))
        
(om/root app-view app-state
  {:target (. js/document (getElementById "app"))})

(defn on-js-reload []
  ;; optionally touch your app-state to force rerendering depending on
  ;; your application
  ;; (swap! app-state update-in [:__figwheel_counter] inc)
)
