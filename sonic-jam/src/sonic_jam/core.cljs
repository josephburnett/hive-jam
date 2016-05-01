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
                               (go (>! (:set-state-ch state) (:ns state))))
                   :style #js {:width "20px"
                               :fontSize "20px"}}
             (str " " (first cursor))))))

(declare grid-view)

(defn track-view [cursor owner]
  (reify
    om/IInitState
    (init-state [_]
      {:track-expanded true})
    om/IWillMount
    (will-mount [_]
      (when (= "grid" (get cursor "type"))
        (go (>! (om/get-state owner :get-state-ch) (get cursor "id")))))
    om/IRenderState
    (render-state [_ state]
      (if-not (:track-expanded state)
        (dom/tr nil (dom/td #js {:onClick #(om/set-state! owner :track-expanded true)
                                 :style #js {:color "#999"}} ">"))
        (dom/tr nil
                (dom/td #js {:onClick #(om/set-state! owner :track-expanded false)
                             :style #js {:color "#999"}} "<")
                (dom/td nil
                 (dom/table nil
                  (apply dom/tr nil
                         (om/build-all cell-view (get cursor "beats") {:state state}))))
                (dom/td nil
                        (when (= "grid" (get cursor "type"))
                          (let [id (get cursor "id")
                                root (om/root-cursor app-state)
                                grids (:grids root)
                                sub-grid (get grids id)
                                sub-state {:set-state-ch (:set-state-ch state)
                                           :get-state-ch (:get-state-ch state)
                                           :ns id}]
                            (om/build grid-view sub-grid {:state sub-state})))))))))

                          
                            

(defn grid-view [cursor owner]
  (reify
    om/IInitState
    (init-state [_]
      {:grid-expanded false})
    om/IRenderState
    (render-state [_ state]
      (dom/div #js {:style #js {:borderLeft "solid 3px #ccc"
                                :paddingLeft "10px"}}
               (if-not (:grid-expanded state)
                 (dom/div nil (dom/p #js {:onClick #(om/set-state! owner :grid-expanded true)
                                          :style #js {:color "#999"}}
                                     (str "[+] " (:ns state))))
                 (dom/div nil (dom/p #js {:onClick #(om/set-state! owner :grid-expanded false)
                                          :style #js {:color "#999"}}
                                     (str "[-] " (:ns state)))
                          (apply dom/table nil
                                 (om/build-all track-view (get cursor "tracks")
                                               {:state state}))))))))

(defn app-view [cursor _]
  (reify
    om/IInitState
    (init-state [_]
      (let [set-state-ch (chan)
            get-state-ch (chan)]
        (go
          (let [{:keys [ws-channel error]} (<! (ws-ch "ws://127.0.0.1:4550/oscbridge"
                                                      {:format :json}))]
            (>! ws-channel {:Address "/get-state" :Params ["root"]})
            (go-loop []
              (let [ns (<! set-state-ch)
                    grid (get-in @cursor [:grids ns])
                    json (js/JSON.stringify (clj->js grid))
                    update #js {:Address "/set-state" :Params #js [ns json]}]
                (go (>! ws-channel update)))
              (recur))
            (go-loop []
              (let [ns (<! get-state-ch)
                    request #js {:Address "/get-state" :Params #js [ns]}]
                (go (>! ws-channel request)))
              (recur))
            (go-loop []
              (let [{:keys [message error] :as msg} (<! ws-channel)
                    params (get message "Params")]
                (when (= "/state" (get message "Address"))
                  (let [grid (js->clj (js/JSON.parse (second params)))]
                    (om/update! cursor [:grids (first params)] grid)))
                (when message
                  (recur))))))
        {:set-state-ch set-state-ch
         :get-state-ch get-state-ch}))
    om/IRenderState
    (render-state [_ state]
      (let [root (get (:grids cursor) "root")]
        (if root
          (dom/div #js {:style #js {:fontFamily "monospace"}}
                   (om/build grid-view root {:state (assoc state :ns "root")}))
          (dom/div nil "loading..."))))))
        
(om/root app-view app-state
  {:target (. js/document (getElementById "app"))})
