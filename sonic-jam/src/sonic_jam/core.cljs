(ns sonic-jam.core
  (:require-macros [cljs.core.async.macros :refer [go-loop]]
                   [cljs.core.async.macros :refer [go]])
  (:require [om.core :as om :include-macros true]
            [om.dom :as dom :include-macros true]
            [cljs.core.async :refer [chan <! >!]]
            [chord.client :refer [ws-ch]]))

(enable-console-print!)

(defonce app-state (atom {:grids {}}))

(defn cell-view [cursor _]
  (reify
    om/IRenderState
    (render-state [_ state]
      (dom/td #js {:onClick #(do
                               (om/update! cursor [(mod (inc (first cursor)) 2)])
                               (go (>! (:sync-grid state) (:ns state))))}
             (str " " (first cursor))))))

(defn track-view [cursor _]
  (reify
    om/IRenderState
    (render-state [_ state]
      (apply dom/tr nil
              (om/build-all cell-view (get cursor "beats") {:state state})))))

(defn grid-view [cursor _]
  (reify
    om/IRenderState
    (render-state [_ state]
      (let [grid (second cursor)
            ns (first cursor)]
        (dom/div nil
                 (dom/h2 nil (get grid "name"))
                 (apply dom/table nil
                        (om/build-all track-view (get grid "tracks")
                                      {:state (assoc state :ns ns)})))))))

(defn app-view [cursor _]
  (reify
    om/IInitState
    (init-state [_]
      (let [sync-channel (chan)]
        (go
          (let [{:keys [ws-channel error]} (<! (ws-ch "ws://127.0.0.1:4550/oscbridge"
                                                      {:format :json}))]
            (>! ws-channel {:Address "/get-state" :Params ["root"]})
            (go-loop []
              (let [ns (<! sync-channel)
                    grid (get-in @cursor [:grids ns])
                    json (js/JSON.stringify (clj->js grid))
                    update #js {:Address "/set-state" :Params #js [ns json]}]
                (go (>! ws-channel update)))
              (recur))
            (go-loop []
              (let [{:keys [message error] :as msg} (<! ws-channel)
                    params (get message "Params")]
                (when (= "/state" (get message "Address"))
                  (let [grid (js->clj (js/JSON.parse (second params)))]
                    (om/update! cursor [:grids (first params)] grid)))
                (when message
                  (recur))))))
        {:sync-grid sync-channel}))
    om/IRenderState
    (render-state [_ state]      
      (apply dom/div nil
             (om/build-all grid-view (seq (:grids cursor)) {:state state})))))
        
(om/root app-view app-state
  {:target (. js/document (getElementById "app"))})
