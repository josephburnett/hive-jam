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
                         

(defn track-editor [{:keys [cursor id delete-ch set-state-ch]} owner]
  (reify
    om/IInitState
    (init-state [_]
      {:state :init})
    om/IRenderState
    (render-state [this state]
      (print cursor)
      (condp = (:state state)
        :init (dom/p #js {:style #js {:color "#999"}}
                     (dom/span #js {:onClick #(om/update-state! owner (fn [s] (merge s {:state :open})))} "{~}"))
        :open (dom/p #js {:style #js {:color "#999"}}
                     (dom/span #js {:onClick #(om/update-state! owner (fn [s] (merge s {:state :init})))} "{- ")
                     (condp = (get cursor "type")
                       "sample" (dom/span nil (str "sample: " (get cursor "sample")))
                       "play" (dom/span nil (str "note: " (get cursor "note")))
                       "grid" (dom/span nil nil)
                       (print "Unable to show open type: " (get cursor "type") " This is a bug."))
                     (dom/span nil
                               (om/build param-editor {:cursor (get cursor "params") :id id :set-state-ch set-state-ch}))
                     (dom/span #js {:onClick #(go (>! delete-ch cursor))} " delete? ")
                     (dom/span nil "}"))))))

(defn new-id []
  (let [letters (map char (range 97 123))]
    (apply str (take 32 (repeatedly #(rand-nth letters))))))

(def style-grey #js {:style #js {:color "#999"}})

(defn track-builder2 [{:keys [cursor id set-state-ch]} owner]
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
                      "synth" (let [track {"type" (:type state)
                                           "beats" (repeat (:width state) [0])
                                           "synth" (:synth state)
                                           "params" {}}
                                    tracks (clj->js (conj cursor track))]
                                (transition {:state :init})
                                (om/update! cursor tracks)
                                (go (>! set-state-ch id)))
                      "grid" (let [track {"type" "grid"
                                          "id" (:text state)
                                          "beats" (repeat (:width state) [0])
                                          "params" {}}
                                   tracks (clj->js (conj cursor track))
                                   grid (clj->js {"name" (:text state)
                                                  "id" (:text state)
                                                  "bpc" (:bpc state)
                                                  "tracks" []})]
                               (transition {:state :init})
                               (let [grids (om/observe owner (grids))]
                                 (om/transact! grids (fn [s] (assoc s (:text state) grid))))
                               (om/update! cursor tracks)
                               (go (>! set-state-ch id))
                               (go (>! set-state-ch (:text state))))
                      "play" (let [track {"type" "play"
                                          "note" (int (:text state))
                                          "beats" (repeat (:width state) [0])
                                          "params" {}}
                                   tracks (clj->js (conj cursor track))]
                               (transition {:state :init})
                               (om/update! cursor tracks)
                               (go (>! set-state-ch id)))
                      (print "Cannot commit."))]
        (condp = (:state state)
          :init (p (dom/span #js {:onClick #(transition {:state :width})} "{+}"))
          :type (p (dom/span #js {:onClick #(transition {:state :init})} (str "{- " (:width state) " ... "))
                   (dom/span #js {:onClick #(transition {:state :sample :type "sample"})} " sample ")
                   (dom/span #js {:onClick #(transition {:state :note :type "play"})} " note ")
                   (dom/span #js {:onClick #(transition {:state :synth :type "synth"})} " synth ")
                   (dom/span #js {:onClick #(transition {:state :bpc :type "grid"})} " grid ")
                   (dom/span nil "}"))
          :width (p (dom/span #js {:onClick #(transition {:state :init})} "{-")
                    (dom/span #js {:onClick #(transition {:state :type :width 1})} " 1 ")
                    (dom/span #js {:onClick #(transition {:state :type :width 2})} " 2 ")
                    (dom/span #js {:onClick #(transition {:state :type :width 3})} " 3 ")
                    (dom/span #js {:onClick #(transition {:state :type :width 4})} " 4 ")
                    (dom/span #js {:onClick #(transition {:state :type :width 6})} " 6 ")
                    (dom/span #js {:onClick #(transition {:state :type :width 8})} " 8 ")
                    (dom/span nil "}"))
          :sample (p (dom/span #js {:onClick #(transition {:state :init})} (str "{- " (:width state) " sample ... "))
                     (apply dom/select #js {:value (:sample state)
                                            :onChange #(handle-change % owner state :sample)
                                            :onClick #(handle-change % owner state :sample)
                                            :onKeyDown #(when (= 13 (.-which %))
                                                          (commit))}
                            (map #(dom/option nil %) (om/observe owner (samples))))
                     (dom/span nil "}"))
          :bpc (p (dom/span #js {:onClick #(transition {:state :init})} (str "{- " (:width state) " grid ... "))
                  (dom/span #js {:onClick #(transition {:state :grid :bpc "1/32"})} " 1/32 ")
                  (dom/span #js {:onClick #(transition {:state :grid :bpc "1/16"})} " 1/16 ")
                  (dom/span #js {:onClick #(transition {:state :grid :bpc "1/8"})} " 1/8 ")
                  (dom/span #js {:onClick #(transition {:state :grid :bpc "1/4"})} " 1/4 ")
                  (dom/span #js {:onClick #(transition {:state :grid :bpc "1/2"})} " 1/2 ")
                  (dom/span #js {:onClick #(transition {:state :grid :bpc "1"})} " 1 ")
                  (dom/span #js {:onClick #(transition {:state :grid :bpc "2"})} " 2 ")
                  (dom/span #js {:onClick #(transition {:state :grid :bpc "4"})} " 4 ")
                  (dom/span #js {:onClick #(transition {:state :grid :bpc "6"})} " 6 ")
                  (dom/span #js {:onClick #(transition {:state :grid :bpc "8"})} " 8 ")
                  (dom/span nil "}"))
          :grid (p (dom/span #js {:onClick #(transition {:state :init})}
                             (str "{- " (:width state) " (" (:bpc state) ") ... "))
                   (dom/input #js {:type "text" :value (:text state)
                                   :onChange #(handle-change % owner state :text)})
                   (dom/span #js {:onClick commit} " commit? ")
                   (dom/span nil "}"))
          :note (p (dom/span #js {:onClick #(transition {:state :init})} (str "{- " (:width state) " note ... "))
                   (dom/input #js {:type "text" :value (:text state)
                                   :onChange #(handle-change % owner state :text)
                                   :onKeyDown #(when (= 13 (.-which %))
                                                 (commit))})
                   (dom/span nil "}"))
          :synth (p (dom/span #js {:onClick #(transition {:state :init})} (str "{- " (:width state) " synth ... "))
                     (apply dom/select #js {:value (:synth state)
                                            :onChange #(handle-change % owner state :synth)
                                            :onClick #(handle-change % owner state :synth)
                                            :onKeyDown #(when (= 13 (.-which %))
                                                          (commit))}
                            (map #(dom/option nil %) (om/observe owner (synths))))
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
                            (om/build track-builder2 {:cursor (get cursor "tracks")
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
