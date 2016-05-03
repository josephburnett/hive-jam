(ns sonic-jam.core
  (:require-macros [cljs.core.async.macros :refer [go-loop]]
                   [cljs.core.async.macros :refer [go]])
  (:require [om.core :as om :include-macros true]
            [om.dom :as dom :include-macros true]
            [cljs.core.async :refer [chan <! >!]]
            [chord.client :refer [ws-ch]]))

(enable-console-print!)

(defonce app-state (atom {:grids {}
                          :samples []}))

(defn grids []
  (om/ref-cursor (:grids (om/root-cursor app-state))))

(defn samples []
  (om/ref-cursor (:samples (om/root-cursor app-state))))

(defn cell-view [{:keys [cursor id]} _]
  (reify
    om/IRenderState
    (render-state [_ state]
      (dom/td #js {:onClick #(do
                               (om/update! cursor [(mod (inc (first cursor)) 2)])
                               (go (>! (:set-state-ch state) id)))
                   :style #js {:width "20px"
                               :fontSize "20px"}}
             (str " " (first cursor))))))

(declare grid-view)

(defn track-view [{:keys [cursor id]} owner]
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
                         (let [cursors (map #(hash-map :cursor %1 :id %2)
                                            (get cursor "beats")
                                            (repeat id))]
                           (om/build-all cell-view cursors {:state state})))))
                (dom/td #js {:style #js {:color "#999" :paddingRight "10px"}} "{~}")
                (dom/td nil
                        (when (= "grid" (get cursor "type"))
                          (let [id (get cursor "id")
                                sub-state {:set-state-ch (:set-state-ch state)
                                           :get-state-ch (:get-state-ch state)}]
                            (om/build grid-view id {:state sub-state})))))))))

(defn track-builder [{:keys [cursor id set-state-ch]} owner]
  (reify
    om/IInitState
    (init-state [_]
      {:state :init})
    om/IRenderState
    (render-state [_ state]
      (let [p (partial dom/p #js {:style #js {:color "#999"}})
            transition #(om/update-state! owner (fn [s] (merge s %)))
            commit #(condp = (:type state)
                      "sample" (let [track {"type" (:type state)
                                            "beats" (repeat (:width state) [0])
                                            "sample" (:sample state)
                                            "params" {}}
                                     tracks (clj->js (conj cursor track))]
                                 (transition {:state :init})
                                 (om/update! cursor tracks)
                                 (go (>! set-state-ch id)))
                      :else (print "cannot commit"))]
        (condp = (:state state)
          :init (p (dom/span #js {:onClick #(transition {:state :width})} "{+}"))
          :width (p (dom/span #js {:onClick #(transition {:state :init})} "{-")
                    (dom/span #js {:onClick #(transition {:state :type :width 1})} " 1 ")
                    (dom/span #js {:onClick #(transition {:state :type :width 2})} " 2 ")
                    (dom/span #js {:onClick #(transition {:state :type :width 4})} " 4 ")
                    (dom/span #js {:onClick #(transition {:state :type :width 8})} " 8 ")
                    (dom/span #js {:onClick #(transition {:state :type :width 16})} " 16 ")
                    (dom/span #js {:onClick #(transition {:state :type :width 32})} " 32 ")
                    (dom/span #js {:onClick #(transition {:state :type :width 64})} " 64 ")
                    (dom/span nil "}"))
          :type (p (dom/span #js {:onClick #(transition {:state :init})} (str "{- " (:width state) " ... "))
                   (dom/span #js {:onClick #(transition {:state :sample :type "sample"})} " sample ")
                   (dom/span #js {:onClick #(print "bonk")} " play ")
                   (dom/span #js {:onClick #(print "bonk")} " synth ")
                   (dom/span nil "}"))
          :sample (p (dom/span #js {:onClick #(transition {:state :init})} (str "{- " (:width state) " sample ... "))
                     (apply dom/select #js {:onChange #(transition {:state :commit :sample (-> % .-target .-value)})}
                            (map #(dom/option nil %) (om/observe owner (samples)) id))
                     (dom/span nil "}"))
          :commit (p (dom/span #js {:onClick #(transition {:state :init})}
                               (str "{- " (:width state) " sample " (:sample state) " ... "))
                     (dom/span #js {:onClick commit} " commit? ")
                     (dom/span nil "}")))))))

(defn grid-view [id owner]
  (reify
    om/IInitState
    (init-state [_]
      {:grid-expanded false})
    om/IRenderState
    (render-state [_ state]
      (let [cursor (get (om/observe owner (grids)) id)]
        (dom/div #js {:style #js {:borderLeft "solid 3px #ccc"
                                  :paddingLeft "10px"}}
                 (if-not (:grid-expanded state)
                   (dom/div nil 
                            (dom/p #js {:onClick #(om/set-state! owner :grid-expanded true)
                                        :style #js {:color "#999"}}
                                   (str "[+] " id)))
                   (dom/div nil 
                            (dom/p #js {:onClick #(om/set-state! owner :grid-expanded false)
                                        :style #js {:color "#999"}}
                                   (str "[-] " id))
                            (apply dom/table nil
                                   (let [cursors (map #(hash-map :cursor %1 :id %2)
                                                      (get cursor "tracks")
                                                      (repeat id))]
                                     (om/build-all track-view cursors {:state state})))
                            (om/build track-builder {:cursor (get cursor "tracks")
                                                     :id id
                                                     :set-state-ch (:set-state-ch state)}))))))))

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
            (>! ws-channel {:Address "/get-samples" :Params []})
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
                (cond 
                  (= "/state" (get message "Address"))
                  (let [grid (js->clj (js/JSON.parse (second params)))]
                    (om/transact! cursor :grids #(assoc % (first params) grid)))
                  (= "/samples" (get message "Address"))
                  (let [samples (js->clj (js/JSON.parse (first params)))]
                    (om/transact! cursor :samples #(into % samples))))
                (when message
                  (recur))))))
        {:set-state-ch set-state-ch
         :get-state-ch get-state-ch}))
    om/IRenderState
    (render-state [_ state]
      (dom/div #js {:style #js {:fontFamily "monospace"}}
               (om/build grid-view "root" {:state state})))))
        
(om/root app-view app-state
  {:target (. js/document (getElementById "app"))})
