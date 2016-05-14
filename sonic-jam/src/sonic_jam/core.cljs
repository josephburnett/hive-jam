(ns sonic-jam.core
  (:require-macros [cljs.core.async.macros :refer [go-loop]]
                   [cljs.core.async.macros :refer [go]])
  (:require [om.core :as om :include-macros true]
            [om.dom :as dom :include-macros true]
            [cljs.core.async :refer [chan put! <! >!]]
            [chord.client :refer [ws-ch]]))

(enable-console-print!)

(defonce app-state (atom {:grids {}
                          :samples []
                          :synths []}))

(defn grids []
  (om/ref-cursor (:grids (om/root-cursor app-state))))

(defn samples []
  (om/ref-cursor (:samples (om/root-cursor app-state))))

(defn synths []
  (om/ref-cursor (:synths (om/root-cursor app-state))))

(defn handle-change [e owner state key]
  (om/set-state! owner key (.. e -target -value)))

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

(def style-grey #js {:style #js {:color "#999"}})

(defn new-id []
  (let [letters (map char (range 97 123))]
    (apply str (take 32 (repeatedly #(rand-nth letters))))))

(declare grid-view)

(defn param-editor [{:keys [cursor id set-state-ch]} owner]
  (reify
    om/IInitState
    (init-state [_]
      {:state :init})
    om/IRenderState
    (render-state [this state]
      (let [commit #(do
                      (om/update-state! owner (fn [s] (merge s {:state :init})))
                      (om/transact! cursor (fn [c] (assoc c (:field state) (:text state))))
                      (go (>! set-state-ch id)))]
        (condp = (:state state)
          :init (dom/table nil
                           (apply dom/tbody nil
                                  (concat
                                   (map (fn [k v] (dom/tr nil
                                                          (dom/td nil k)
                                                          (dom/td #js {:onClick #(om/update-state! owner
                                                                                                   (fn [s] (merge s {:state :editing
                                                                                                                     :text v
                                                                                                                     :field k})))}
                                                                  v)))
                                        (keys cursor)
                                        (vals cursor))
                                   [(dom/td #js {:onClick #(om/update-state! owner
                                                                             (fn [s] (merge s {:state :adding-field
                                                                                               :text nil
                                                                                               :field ""})))}
                                            "{+}")])))
          :editing (dom/table nil
                              (apply dom/tbody nil
                                     (concat
                                      (map (fn [k v] (dom/tr nil
                                                             (dom/td nil k)
                                                             (if (= k (:field state))
                                                               (dom/td nil
                                                                       (dom/input #js {:type "text" :value (:text state)
                                                                                       :onChange #(handle-change % owner state :text)
                                                                                       :onKeyDown #(when (= 13 (.-which %))
                                                                                                     (commit))}))
                                                               (dom/td #js {:onClick #(om/update-state! owner
                                                                                                        (fn [s] (merge s {:state :editing
                                                                                                                          :text v
                                                                                                                          :field k})))}
                                                                       v))))
                                           (keys cursor)
                                           (vals cursor))
                                      [(dom/td #js {:onClick #(om/update-state! owner
                                                                                (fn [s] (merge s {:state :adding-field
                                                                                                  :text nil
                                                                                                  :field ""})))}
                                               "{+}")])))
          :adding-field (dom/table nil
                                   (concat
                                    (map (fn [k v] (dom/tr nil
                                                           (dom/td nil k)
                                                           (dom/td #js {:onClick #(om/update-state! owner
                                                                                                    (fn [s] (merge s {:state :editing
                                                                                                                      :text v
                                                                                                                      :field k})))}
                                                                   v)))
                                         (keys cursor)
                                         (vals cursor))
                                    [(dom/tr nil
                                             (dom/td nil
                                                     (dom/input #js {:type "text" :value (:field state)
                                                                     :onChange #(handle-change % owner state :field)
                                                                     :onKeyDown #(when (= 13 (.-which %))
                                                                                   (om/update-state! owner
                                                                                                     (fn [s] (merge {:state :adding-value
                                                                                                                     :field (:field state)}))))}))
                                             (dom/td nil nil))]))
          :adding-value (dom/table nil
                                   (concat
                                    (map (fn [k v] (dom/tr nil
                                                           (dom/td nil k)
                                                           (dom/td #js {:onClick #(om/update-state! owner
                                                                                                    (fn [s] (merge s {:state :editing
                                                                                                                      :text v
                                                                                                                      :field k})))}
                                                                   v)))
                                         (keys cursor)
                                         (vals cursor))
                                    [(dom/tr nil
                                             (dom/td nil (:field state))
                                             (dom/td nil
                                                     (dom/input #js {:type "text" :value (:text state)
                                                                     :onChange #(handle-change % owner state :text)
                                                                     :onKeyDown #(when (= 13 (.-which %))
                                                                                   (commit))})))])))))))
                         

(defn type-editor [{:keys [cursor id set-state-ch]} owner]
  (reify
    om/IInitState
    (init-state [_]
      {:state :init
       :type (get cursor "type")})
    om/IRenderState
    (render-state [this state]
      (condp = (:state state)
        :init (dom/span #js {:onClick #(om/update-state! owner (fn [s] (merge s {:state :selecting})))}
                        (get cursor "type"))
        :selecting (dom/select #js {:value (:type state)
                                    :onChange #(handle-change % owner state :type)
                                    :onClick #(handle-change % owner state :type)
                                    :onKeyDown #(when (= 13 (.-which %))
                                                  (om/update-state! owner (fn [s] (merge s {:state :init})))
                                                  (om/transact! cursor (fn [c] (assoc c "type" (:type state))))
                                                  (when-not (contains? cursor "id")
                                                    (let [grid {"name" "sub-grid"
                                                                "id" (new-id)
                                                                "bpc" 1
                                                                "tracks" []}]
                                                      (om/transact! (om/observe owner (grids))
                                                                    (fn [c] (assoc c (get grid "id") grid)))
                                                      (go (>! set-state-ch (get grid "id")))))
                                                  (go (>! set-state-ch id)))}
                               (dom/option nil "synth")
                               (dom/option nil "sample")
                               (dom/option nil "play")
                               (dom/option nil "grid"))))))

(defn track-editor [{:keys [cursor id delete-ch set-state-ch] :as input} owner]
  (reify
    om/IInitState
    (init-state [_]
      {:state :init})
    om/IRenderState
    (render-state [this state]
      (let [closer #js {:onClick  #(om/update-state! owner (fn [s] (merge s {:state :init})))}]
        (condp = (:state state)
          :init (dom/p style-grey
                       (dom/span #js {:onClick #(om/update-state! owner (fn [s] (merge s {:state :open})))} "{..}"))
          :open (dom/p style-grey
                       (dom/table nil
                                  (dom/tbody nil
                                             (dom/tr nil
                                                     (dom/td closer "{")
                                                     (dom/td nil nil)
                                                     (dom/td nil nil))
                                             (dom/tr nil
                                                     (dom/td nil nil)
                                                     (dom/td nil "type:")
                                                     (dom/td nil (om/build type-editor input)))
                                             (condp = (get cursor "type")
                                               "synth"
                                               (dom/tr nil
                                                       (dom/td nil nil)
                                                       (dom/td nil "synth:")
                                                       (dom/td nil (get cursor "synth")))
                                               "sample"
                                               (dom/tr nil
                                                       (dom/td nil nil)
                                                       (dom/td nil "sample:")
                                                       (dom/td nil (get cursor "sample")))
                                               "play"
                                               (dom/tr nil
                                                       (dom/td nil nil)
                                                       (dom/td nil "note:")
                                                       (dom/td nil (get cursor "note")))
                                               (print "Unable to show open type. This is a bug."))
                                             (dom/tr nil
                                                     (dom/td closer "}")
                                                     (dom/td nil nil)
                                                     (dom/td nil))))))))))

(defn track-builder [{:keys [cursor id set-state-ch]} owner]
  (reify
    om/IInitState
    (init-state [_]
      {:state :init})
    om/IRenderState
    (render-state [_ state]
      (let [transition #(om/update-state! owner (fn [s] (merge s %)))
            set-width #(om/transact!
                        cursor (fn [s]
                                 (clj->js
                                  (conj s {"type" "none"
                                           "id" (new-id)
                                           "beats" (repeat % [0])
                                           "params" {}}))))
            width-selection (fn [w s]
                              (dom/span #js {:onClick
                                             #(do
                                                (set-width w)
                                                (transition {:state :init})
                                                (go (>! set-state-ch id)))} s))]
        (condp = (:state state)
          :init (let [width (apply max (map #(count (get % "beats")) cursor))]
                  (if (= 0 (count cursor))
                    (dom/p style-grey
                           (dom/span #js {:onClick #(transition {:state :open})} "{+}"))
                    (dom/p style-grey
                           (dom/span #js {:onClick #(transition {:state :open})} "{+")
                           (width-selection width (str " " width))
                           (dom/span nil "}"))))
          :open (dom/p style-grey
                       (dom/span #js {:onClick #(transition {:state :init})} "{- ")
                       (width-selection 1 " 1")
                       (width-selection 2 " 2")
                       (width-selection 4 " 4")
                       (width-selection 8 " 8")
                       (width-selection 16 " 16")
                       (dom/span nil "}")))))))

(defn track-view [{:keys [cursor id delete-ch]} owner]
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
                (dom/td #js {:style #js {:color "#999" :paddingRight "10px"}}
                        (om/build track-editor {:cursor cursor :id id :delete-ch delete-ch
                                                :set-state-ch (:set-state-ch state)}))
                (dom/td nil
                        (when (= "grid" (get cursor "type"))
                          (let [id (get cursor "id")
                                sub-state {:set-state-ch (:set-state-ch state)
                                           :get-state-ch (:get-state-ch state)}]
                            (om/build grid-view id {:state sub-state})))))))))

(defn grid-view [id owner]
  (reify
    om/IInitState
    (init-state [_]
      (let [delete-ch (chan)]
        {:grid-expanded false
         :delete-ch delete-ch}))
    om/IWillMount
    (will-mount [this]
      (let [delete-ch (:delete-ch (om/get-state owner))
            set-state-ch (:set-state-ch (om/get-state owner))]
        (go-loop []
          (let [track (<! delete-ch)
                cursor (get-in (om/observe owner (grids)) [id "tracks"])]
            (om/update! cursor (vec (remove (partial = track) cursor)))
            (put! set-state-ch id)
            (recur)))))
    om/IRenderState
    (render-state [_ state]
      (let [cursor (get (om/observe owner (grids)) id)]
        (dom/div #js {:style #js {:borderLeft "solid 3px #ccc"
                                  :paddingLeft "10px"}}
                 (if-not (:grid-expanded state)
                   (dom/div nil 
                            (dom/p #js {:onClick #(om/set-state! owner :grid-expanded true)
                                        :style #js {:color "#999"}}
                                   (str "[+] " id " (" (get cursor "bpc") ")")))
                   (dom/div nil 
                            (dom/p #js {:onClick #(om/set-state! owner :grid-expanded false)
                                        :style #js {:color "#999"}}
                                   (str "[-] " id " (" (get cursor "bpc") ")"))
                            (dom/table nil
                                       (apply dom/tbody nil
                                              (let [cursors (map #(hash-map :cursor %1 :id %2 :delete-ch %3)
                                                                 (get cursor "tracks")
                                                                 (repeat id)
                                                                 (repeat (:delete-ch state)))]
                                                (om/build-all track-view cursors {:state state}))))
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
            (>! ws-channel {:Address "/get-synths" :Params []})
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
                    (om/transact! cursor :samples #(into % samples)))
                  (= "/synths" (get message "Address"))
                  (let [synths (js->clj (js/JSON.parse (first params)))]
                    (om/transact! cursor :synths #(into % synths))))
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
