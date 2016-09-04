(ns hive-jam.core
  (:require-macros [cljs.core.async.macros :refer [go-loop]]
                   [cljs.core.async.macros :refer [go]])
  (:require [om.core :as om :include-macros true]
            [om.dom :as dom :include-macros true]
            [cljs.core.async :refer [chan put! <! >!]]
            [chord.client :refer [ws-ch]]
            [goog.dom :as gdom]
            [goog.events :as gevents])
  (:import [goog.ui Button Menu MenuItem PopupMenu SubMenu Component
            FlatMenuButtonRenderer FlatButtonRenderer]
           [goog.positioning Corner]))

(enable-console-print!)

;; Solarized Dark
(def pallette {:yellow "#b58900"
               :orange "#cb4b16"
               :red "#dc322f"
               :magenta "#d33682"
               :violet "#6c71c4"
               :blue "#268bd2"
               :faded-blue "#134569" ; custom color
               :cyan "#2aa198"
               :green "#859900"
               :base03 "#002b36"
               :base02 "#073642"
               :base0102 "#1B444E" ; custom color: between base01 and base02
               :base01 "#586e75"
               :base00 "#657b83"
               :base0 "#839496"
               :base1 "#93a1a1"
               :base2 "#eee8d5"
               :base3 "#fdf6e3"})

(def theme {:background (:base03 pallette)
            :foreground (:base1 pallette)
            :cursorOn (:red pallette)
            :cursorOff (:base0102 pallette)
            :on (:green pallette)
            :off (:base02 pallette)
            :link (:blue pallette)
            :link-bar (:faded-blue pallette)
            :bar (:base02 pallette)})
  
(defonce app-state (atom {:grids {}
                          :samples []
                          :synths []
                          :grid-types ["synth" "sample"]
                          :bpc-values ["1/32" "1/16" "1/8" "1/4" "1/2"
                                       "1" "2" "4" "8" "16" "32"]
                          :errors []
                          :console []
                          :beat-cursors []
                          :hive {}}))

(defn config [key]
  (get (js->clj js/HJ_CONFIG) key))

(defn grids []
  (om/ref-cursor (:grids (om/root-cursor app-state))))

(defn grid-types []
  (om/ref-cursor (:grid-types (om/root-cursor app-state))))

(defn samples []
  (om/ref-cursor (:samples (om/root-cursor app-state))))

(defn synths []
  (om/ref-cursor (:synths (om/root-cursor app-state))))

(defn bpc-values []
  (om/ref-cursor (:bpc-values (om/root-cursor app-state))))

(defn handle-change [e owner state key]
  (om/set-state! owner key (.. e -target -value)))

(defn cell-view [{:keys [cursor id cursor-on track-on]} _]
  (reify
    om/IRenderState
    (render-state [_ state]
      (let [cell-on (not (= 0 (first cursor)))
            color (if (not cursor-on) ; cursor is not on this cell
                    (if (and cell-on track-on)
                      (:on theme)
                      (:off theme))
                    (if (and cell-on track-on)
                      (:cursorOn theme)
                      (:cursorOff theme)))]
        (dom/td #js {:onClick #(do
                                 (om/update! cursor [(if (= 0 (first cursor)) 1 0)])
                                 (go (>! (:set-state-ch state) id)))
                     :onContextMenu #(do
                                       (om/update! cursor [(inc (first cursor))])
                                       (go (>! (:set-state-ch state) id))
                                       false)
                     :style #js {:width "20px"
                                 :fontSize "20px"
                                 :fontWeight (if (and cursor-on cell-on)
                                               "bold" "normal")
                                 :color color}}
                (str " " (first cursor)))))))

(def style-grey #js {:style #js {:color (:foreground theme)}})

(defn new-id []
  (let [letters (map char (range 97 123))]
    (apply str (take 8 (repeatedly #(rand-nth letters))))))

(declare grid-view)

(defn param-editor-builder [key]
  (fn [{:keys [cursor id set-state-ch]} owner]
    (reify
      om/IInitState
      (init-state [_]
        {:state :init})
      om/IRenderState
      (render-state [this state]
        (let [cursor (get cursor key)]
          (let [commit #(do
                          (om/update-state! owner (fn [s] (merge s {:state :open})))
                          (om/transact! cursor (fn [c] (assoc c (:field state) (:text state))))
                          (go (>! set-state-ch id)))
                closer #js {:onClick #(om/update-state! owner (fn [s] (merge s {:state :init})))
                            :style #js {:color (:link theme)}}
                canceller #js {:onClick #(om/update-state! owner (fn [s] (merge s {:state :open})))
                               :style #js {:color (:link theme)}}
                delete #(do
                          (om/transact! cursor (fn [c] (dissoc c %)))
                          (go (>! set-state-ch id)))]
            (condp = (:state state)
              :init (dom/span #js {:onClick #(om/update-state! owner (fn [s] (merge s {:state :open})))
                                   :style #js {:color (:link theme)}} "{..}")
              :open (dom/table nil
                               (apply dom/tbody nil
                                      (concat
                                       [(dom/tr nil
                                                (dom/td closer "{")
                                                (dom/td nil nil)
                                                (dom/td nil nil))]
                                       (map (fn [k v] (dom/tr nil
                                                              (dom/td #js {:onClick #(delete k)
                                                                           :style #js {:color (:link theme)}} "X")
                                                              (dom/td nil (str k ":"))
                                                              (dom/td #js {:onClick #(om/update-state! owner
                                                                                                       (fn [s] (merge s {:state :editing
                                                                                                                         :text v
                                                                                                                         :field k})))
                                                                           :style #js {:color (:link theme)}}
                                                                      v)))
                                            (keys cursor)
                                            (vals cursor))
                                       [(dom/tr nil
                                                (dom/td nil nil)
                                                (dom/td #js {:onClick #(om/update-state! owner
                                                                                         (fn [s] (merge s {:state :adding-field
                                                                                                           :text nil
                                                                                                           :field ""})))
                                                             :style #js {:color (:link theme)}}
                                                        "[+]")
                                                (dom/td nil nil))
                                        (dom/tr nil
                                                (dom/td closer "}")
                                                (dom/td nil nil)
                                                (dom/td nil nil))])))
              :editing (dom/table nil
                                  (apply dom/tbody nil
                                         (concat
                                          [(dom/tr closer
                                                   (dom/td closer "{")
                                                   (dom/td nil nil)
                                                   (dom/td nil nil))]
                                          (map (fn [k v] (dom/tr nil
                                                                 (dom/td #js {:onClick #(delete k)
                                                                              :style #js {:color (:link theme)}} "X")
                                                                 (dom/td nil (str k ":"))
                                                                 (if (= k (:field state))
                                                                   (dom/td nil
                                                                           (dom/input #js {:type "text" :value (:text state)
                                                                                           :onChange #(handle-change % owner state :text)
                                                                                           :onKeyDown #(when (= 13 (.-which %))
                                                                                                         (commit))}))
                                                                   (dom/td #js {:onClick #(om/update-state! owner
                                                                                                            (fn [s] (merge s {:state :editing
                                                                                                                              :text v
                                                                                                                              :field k})))
                                                                                :style #js {:color (:link theme)}}
                                                                           v))))
                                               (keys cursor)
                                               (vals cursor))
                                          [(dom/tr nil
                                                   (dom/td nil nil)
                                                   (dom/td #js {:onClick #(om/update-state! owner
                                                                                            (fn [s] (merge s {:state :adding-field
                                                                                                              :text nil
                                                                                                              :field ""})))
                                                                :style #js {:color (:link theme)}}
                                                           "[+]")
                                                   (dom/td nil nil))
                                           (dom/tr nil
                                                   (dom/td closer "}")
                                                   (dom/td nil nil)
                                                   (dom/td nil nil))])))
              :adding-field (dom/table nil
                                       (concat
                                        [(dom/tr nil
                                                 (dom/td closer "{")
                                                 (dom/td nil nil)
                                                 (dom/td nil nil))]
                                        (map (fn [k v] (dom/tr nil
                                                               (dom/td #js {:onClick #(delete k)
                                                                            :style #js {:color (:link theme)}} "X")
                                                               (dom/td nil (str k ":"))
                                                               (dom/td #js {:onClick #(om/update-state! owner
                                                                                                        (fn [s] (merge s {:state :editing
                                                                                                                          :text v
                                                                                                                          :field k})))
                                                                            :style #js {:color (:link theme)}}
                                                                       v)))
                                             (keys cursor)
                                             (vals cursor))
                                        [(dom/tr nil
                                                 (dom/td nil nil)
                                                 (dom/td nil
                                                         (dom/span canceller "[- ")
                                                         (dom/input #js {:type "text" :value (:field state)
                                                                         :onChange #(handle-change % owner state :field)
                                                                         :onKeyDown #(when (= 13 (.-which %))
                                                                                       (om/update-state! owner
                                                                                                         (fn [s] (merge {:state :adding-value
                                                                                                                         :field (:field state)}))))}))
                                                 (dom/td nil " ]"))
                                         (dom/tr nil
                                                 (dom/td closer "}")
                                                 (dom/td nil nil)
                                                 (dom/td nil nil))]))
              :adding-value (dom/table nil
                                       (concat
                                        [(dom/tr nil
                                                 (dom/td closer "}")
                                                 (dom/td nil nil)
                                                 (dom/td nil nil))]
                                        (map (fn [k v] (dom/tr nil
                                                               (dom/td #js {:onClick #(delete k)
                                                                            :style #js {:color (:link theme)}} "X")
                                                               (dom/td nil (str k ":"))
                                                               (dom/td #js {:onClick #(om/update-state! owner
                                                                                                        (fn [s] (merge s {:state :editing
                                                                                                                          :text v
                                                                                                                          :field k})))
                                                                            :style #js {:color (:link theme)}}
                                                                       v)))
                                             (keys cursor)
                                             (vals cursor))
                                        [(dom/tr nil
                                                 (dom/td nil nil)
                                                 (dom/td nil
                                                         (dom/span canceller "[- ")
                                                         (dom/span nil (str (:field state) ":")))
                                                 (dom/td nil
                                                         (dom/input #js {:type "text" :value (:text state)
                                                                         :onChange #(handle-change % owner state :text)
                                                                         :onKeyDown #(when (= 13 (.-which %))
                                                                                       (commit))})
                                                         (dom/span nil " ]")))
                                         (dom/tr nil
                                                 (dom/td closer "}")
                                                 (dom/td nil nil)
                                                 (dom/td nil nil))])))))))))

(def param-editor
  (param-editor-builder "params"))

(def synth-param-editor
  (param-editor-builder "synth-params"))

(def sample-param-editor
  (param-editor-builder "sample-params"))

(defn text-editor-builder [key]
  (fn [{:keys [cursor id set-state-ch]} owner]
    (reify
      om/IInitState
      (init-state [_]
        {:state (if (and (contains? cursor key)
                         (not (= "" (get cursor key))))
                  :init :editing)
         :value (get cursor key)})
      om/IRenderState
      (render-state [_ state]
        (condp = (:state state)
          :init
          (dom/span #js {:onClick #(om/update-state! owner (fn [s] (merge s {:state :editing})))
                         :style #js {:color (:link theme)}}
                    (get cursor key))
          :editing
          (dom/input #js {:type "text" :value (:value state)
                          :onChange #(handle-change % owner state :value)
                          :onKeyDown #(when (= 13 (.-which %))
                                        (om/update-state! owner (fn [s] (merge s {:state :init})))
                                        (om/transact! cursor (fn [c] (assoc c key (:value state))))
                                        (go (>! set-state-ch id)))}))))))

(def name-editor
  (text-editor-builder "name"))

(defn select-editor-builder
  ([key options-ref]
   (fn [{:keys [cursor id set-state-ch]} owner]
     (let [render-menu #(let [node (om/get-node owner)
                             menu (PopupMenu.)
                             options (om/observe owner (options-ref))
                             click-fn (fn [value]
                                        (om/transact! cursor (fn [c] (assoc c key value)))
                                        (go (>! set-state-ch id)))]
                         (.attach menu node Corner.BOTTOM_START)
                         (doall (map (fn [o] (.addItem menu (MenuItem. o))) options))
                         (.render menu)
                         (gevents/listen menu Component.EventType.ACTION
                                         (fn [e] (let [value (.getValue e.target)]
                                                   (om/update-state! owner (fn [s] (merge s {:state :init})))
                                                   (click-fn value)))))]
     (reify
       om/IInitState
       (init-state [_]
         {:state :init
          :id (new-id)})
       om/IDidMount
       (did-mount [_] (render-menu))
       om/IDidUpdate
       (did-update [_ _ _] (render-menu))
       om/IRenderState
       (render-state [_ state]
         (if (and (= :init (:state state))
                  (contains? cursor key)
                  (not (= "none" (get cursor key))))
           (dom/span #js {:onClick #(om/update-state! owner (fn [s] (merge s {:state :selecting})))
                          :style #js {:color (:link theme)}}
                     (get cursor key))
           (dom/div #js {:className "goog-flat-menu-button"}
                    (str "Choose a " key)))))))))

(def grid-type-editor
  (select-editor-builder "grid-type" grid-types))

(def synth-editor
  (select-editor-builder "synth" synths))

(def sample-editor
  (select-editor-builder "sample" samples))

(def bpc-editor
  (select-editor-builder "bpc" bpc-values))

(defn deep-copy [grids-cursor copy-id set-state-ch]
  (let [grid (get grids-cursor copy-id)
        copy-id (new-id)
        copy (assoc grid
                    "tracks" (clj->js (map #(if (= "grid" (get % "type"))
                                              (let [grid-id (get % "grid-id")]
                                                (assoc % "grid-id" (deep-copy grids-cursor grid-id set-state-ch)))
                                              %)
                                           (get grid "tracks")))
                    "id" copy-id)]
    (om/transact! grids-cursor #(assoc % copy-id copy))
    (go (>! set-state-ch copy-id))
    copy-id))

(defn type-editor [{:keys [cursor id set-state-ch]} owner]
  (let [render-menu #(let [node (om/get-node owner)
                           grid-copy-menu (:grid-copy-menu (om/get-state owner))
                           menu (PopupMenu.)
                           sub-menu (SubMenu. "grid")
                           click-fn (fn [value]
                                      (cond
                                        (contains? grid-copy-menu value)
                                        (let [grid-id (get grid-copy-menu value)
                                              copy-id (deep-copy (om/observe owner (grids)) grid-id set-state-ch)]
                                          (om/transact! cursor (fn [c] (assoc c
                                                                              "type" "grid"
                                                                              "synth-params" {}
                                                                              "sample-params" {}
                                                                              "grid-id" copy-id))))
                                        (= "new" value)
                                        (let [grid-id (new-id)
                                              grid {"name" ""
                                                    "id" grid-id
                                                    "bpc" "1"
                                                    "tracks" []}]
                                          (om/transact! cursor (fn [c] (assoc c
                                                                              "type" "grid"
                                                                              "synth-params" {}
                                                                              "sample-params" {}
                                                                              "grid-id" grid-id)))
                                          (om/transact! (om/observe owner (grids))
                                                        (fn [c] (assoc c (get grid "id") grid)))
                                          (go (>! set-state-ch grid-id)))
                                        (= "sample" value)
                                        (om/transact! cursor (fn [c] (assoc c
                                                                            "type" value
                                                                            "sample-params" {})))
                                        (= "synth" value)
                                        (om/transact! cursor (fn [c] (assoc c
                                                                            "type" value
                                                                            "synth-params" {})))
                                        :else (print (str "Could not find: " value ". Was it renamed?")))
                                      (go (>! set-state-ch id)))]
                       (.attach menu node Corner.BOTTOM_START)
                       (.addItem menu (MenuItem. "synth"))
                       (.addItem menu (MenuItem. "sample"))
                       (.addItem menu sub-menu)
                       (.addItem sub-menu (MenuItem. "new"))
                       (doall (map (fn [o] (.addItem sub-menu (MenuItem. o))) (keys grid-copy-menu)))
                       (.render menu)
                       (gevents/listen menu Component.EventType.ACTION
                                       (fn [e] (let [value (.getValue e.target)]
                                                 (om/update-state! owner (fn [s] (merge s {:state :init})))
                                                 (click-fn value)))))]
    (reify
      om/IInitState
      (init-state [_]
        {:state :init
         :id (new-id)
         :grid-copy-menu {}})
      om/IDidMount
      (did-mount [_] (render-menu))
      om/IDidUpdate
      (did-update [_ _ _] (render-menu))
      om/IRenderState
      (render-state [_ state]
        (let [grid-copy-menu (apply hash-map
                                    (apply concat (map #(let [id (first %)
                                                              grid (second %)]
                                                          (if (and (contains? grid "name")
                                                                   (not (= "" (get grid "name"))))
                                                            [(str "copy of " (get grid "name") " (" id ")") id]
                                                            [(str "copy of " id) id]))
                                                       (om/observe owner (grids)))))]
          (om/set-state! owner :grid-copy-menu grid-copy-menu))
        (if (and (= :init (:state state))
                 (contains? cursor "type")
                 (not (= "none" (get cursor "type"))))
          (dom/span #js {:onClick #(om/update-state! owner (fn [s] (merge s {:state :selecting})))
                         :style #js {:color (:link theme)}}
                    (get cursor "type"))
          (dom/div #js {:className "goog-flat-menu-button"}
                   (str "Choose a type")))))))

(defn fx-editor [{:keys [cursor id set-state-ch]} owner]
  (reify
    om/IInitState
    (init-state [_]
      {:state :init})
    om/IRenderState
    (render-state [_ state]
      (let [transition #(om/update-state! owner (fn [s] (merge {:state %})))
            closing-row #(dom/tr nil
                                 (dom/td #js {:onClick (fn [] (transition :init))
                                              :style #js {:color (:link theme)}} %)
                                 (dom/td nil nil)
                                 (dom/td nil nil))
            remove #(do
                      (om/transact! cursor (fn [c] (vec (into (subvec c 0 %)
                                                              (subvec c (+ 1 %))))))
                      (go (>! set-state-ch id)))
            body-rows (map #(dom/tr nil
                                    (dom/td #js {:onClick (fn [] (remove %2))
                                                 :style #js {:color (:link theme)}} "X")
                                    (dom/td nil (get %1 "fx"))
                                    (dom/td nil (om/build param-editor
                                                          {:cursor %1
                                                           :id id
                                                           :set-state-ch set-state-ch}
                                                          {:key :id})))
                           cursor
                           (range))
            commit #(let [new-fx {:fx (:fx state)
                                  :id (new-id)
                                  :params {}}]
                      (transition :open)
                      (om/transact! cursor (fn [c] (clj->js (into c [new-fx]))))
                      (go (>! set-state-ch id)))]
        (condp = (:state state)
          :init (dom/span #js {:onClick #(transition :open)
                               :style #js {:color (:link theme)}}
                          "{..}")
          :open (dom/table nil
                           (apply dom/tbody nil
                                  (concat
                                   [(closing-row "{")]
                                   body-rows
                                   [(dom/tr nil
                                            (dom/td nil nil)
                                            (dom/td #js {:onClick #(transition :adding)
                                                         :style #js {:color (:link theme)}} "[+]")
                                            (dom/td nil nil))
                                    (closing-row "}")])))
          :adding (dom/table nil
                             (apply dom/tbody  nil
                                    (concat
                                     [(closing-row "{")]
                                     body-rows
                                     [(dom/tr nil
                                              (dom/td nil nil)
                                              (dom/td nil
                                                      (dom/input #js {:type "text" :value (:fx state)
                                                                      :onChange #(handle-change % owner state :fx)
                                                                      :onKeyDown #(when (= 13 (.-which %)) (commit))}))
                                              (dom/td nil nil))

                                      (closing-row "}")]))))))))

(defn track-editor [{:keys [cursor id delete-ch set-state-ch] :as input} owner]
  (reify
    om/IInitState
    (init-state [_]
      {:state :init})
    om/IRenderState
    (render-state [this state]
      (let [closer #js {:onClick  #(om/update-state! owner (fn [s] (merge s {:state :init})))
                        :style #js {:color (:link theme)}}
            turn-on #(do
                       (om/transact! cursor (fn [c] (assoc c "on" true)))
                       (go (>! set-state-ch id)))
            turn-off #(do
                        (om/transact! cursor (fn [c] (assoc c "on" false)))
                        (go (>! set-state-ch id)))]
        (condp = (:state state)
          :init (dom/p style-grey
                       (dom/span #js {:onClick #(om/update-state! owner (fn [s] (merge s {:state :open})))
                                      :style #js {:color (:link theme)}} "{..}"))
          :open (let [fx-row (dom/tr nil
                                     (dom/td nil nil)
                                     (dom/td nil "fx:")
                                     (dom/td nil (om/build fx-editor {:cursor (get cursor "fx")
                                                                      :id id
                                                                      :set-state-ch set-state-ch})))]
                  (dom/p style-grey
                         (dom/table nil
                                    (apply dom/tbody nil
                                           (concat
                                            [(dom/tr nil
                                                     (dom/td nil nil)
                                                     (dom/td nil nil)
                                                     (dom/td nil (dom/span #js {:onClick #(go (>! delete-ch cursor))
                                                                                :style #js {:color (:link theme)
                                                                                            :float "right"}} "X")))
                                             (dom/tr nil
                                                     (dom/td closer "{")
                                                     (dom/td nil nil)
                                                     (dom/td nil nil))
                                             (dom/tr nil
                                                     (dom/td nil nil)
                                                     (dom/td nil "track:")
                                                     (dom/td nil (if (get cursor "on")
                                                                   (dom/span #js {:onClick turn-off
                                                                                  :style #js {:color (:link theme)}} "on")
                                                                   (dom/span #js {:onClick turn-on
                                                                                  :style #js {:color (:link theme)}} "off"))))
                                             (dom/tr nil
                                                     (dom/td nil nil)
                                                     (dom/td nil "type:")
                                                     (dom/td nil (om/build type-editor input)))]
                                            (condp = (get cursor "type")
                                              "synth" [(dom/tr nil
                                                               (dom/td nil nil)
                                                               (dom/td nil "synth:")
                                                               (dom/td nil (om/build synth-editor input)))]
                                              "sample" [(dom/tr nil
                                                                (dom/td nil nil)
                                                                (dom/td nil "sample:")
                                                                (dom/td nil (om/build sample-editor input)))]
                                              "play" [(dom/tr nil
                                                              (dom/td nil nil)
                                                              (dom/td nil "note:")
                                                              (dom/td nil (get cursor "note")))]
                                              [])
                                            (condp = (get cursor "type")
                                              "none" []
                                              "grid" (condp = (get cursor "grid-type")
                                                       "synth" [(dom/tr nil
                                                                        (dom/td nil nil)
                                                                        (dom/td nil "grid-type:")
                                                                        (dom/td nil (om/build grid-type-editor input)))
                                                                (dom/tr nil
                                                                        (dom/td nil nil)
                                                                        (dom/td nil "synth:")
                                                                        (dom/td nil (om/build synth-editor input)))
                                                                (dom/tr nil
                                                                        (dom/td nil nil)
                                                                        (dom/td nil "params:")
                                                                        (dom/td nil (om/build synth-param-editor input)))
                                                                fx-row]
                                                       "sample" [(dom/tr nil
                                                                         (dom/td nil nil)
                                                                         (dom/td nil "grid-type:")
                                                                         (dom/td nil (om/build grid-type-editor input)))
                                                                 (dom/tr nil
                                                                         (dom/td nil nil)
                                                                         (dom/td nil "sample:")
                                                                         (dom/td nil (om/build sample-editor input)))
                                                                 (dom/tr nil
                                                                         (dom/td nil nil)
                                                                         (dom/td nil "params:")
                                                                         (dom/td nil (om/build sample-param-editor input)))
                                                                 fx-row]
                                                       [(dom/tr nil
                                                                (dom/td nil nil)
                                                                (dom/td nil "grid-type:")
                                                                (dom/td nil (om/build grid-type-editor input)))
                                                        fx-row])
                                              "sample" [(dom/tr nil
                                                                (dom/td nil nil)
                                                                (dom/td nil "params:")
                                                                (dom/td nil (om/build sample-param-editor input)))
                                                        fx-row]
                                              "synth" [(dom/tr nil
                                                               (dom/td nil nil)
                                                               (dom/td nil "params:")
                                                               (dom/td nil (om/build synth-param-editor input)))
                                                       fx-row])
                                            [(dom/tr nil
                                                     (dom/td closer "}")
                                                     (dom/td nil nil)
                                                     (dom/td nil))]))))))))))

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
                                           "on" true
                                           "beats" (repeat % [0])
                                           "fx" []}))))
            width-selection (fn [w s]
                              (dom/span #js {:onClick
                                             #(do
                                                (set-width w)
                                                (transition {:state :init})
                                                (go (>! set-state-ch id)))
                                             :style #js {:color (:link theme)}} s))]
        (condp = (:state state)
          :init (let [width (apply max (map #(count (get % "beats")) cursor))]
                  (if (= 0 (count cursor))
                    (dom/p style-grey
                           (dom/span #js {:onClick #(transition {:state :open})
                                          :style #js {:color (:link theme)}} "[+]"))
                    (dom/p style-grey
                           (dom/span #js {:onClick #(transition {:state :open})
                                          :style #js {:color (:link theme)}} "[+")
                           (width-selection width (str " " width))
                           (dom/span nil "]"))))
          :open (dom/p style-grey
                       (dom/span #js {:onClick #(transition {:state :init})
                                      :style #js {:color (:link theme)}} "[- ")
                       (width-selection 1 " 1")
                       (width-selection 2 " 2")
                       (width-selection 4 " 4")
                       (width-selection 8 " 8")
                       (width-selection 16 " 16")
                       (dom/span nil "]")))))))

(defn track-view [{:keys [cursor id beat-cursors delete-ch]} owner]
  (reify
    om/IInitState
    (init-state [_]
      {:track-expanded true})
    om/IWillMount
    (will-mount [_]
      (when (= "grid" (get cursor "type"))
        (go (>! (om/get-state owner :get-state-ch) (get cursor "grid-id")))))
    om/IRenderState
    (render-state [_ state]
      (if-not (:track-expanded state)
        (dom/tr nil
                (dom/td #js {:onClick #(om/set-state! owner :track-expanded true)
                             :style #js {:color (:link theme)}} ">"))
        (dom/tr nil
                (dom/td nil
                 (dom/table nil
                  (apply dom/tr nil
                         (let [cursors (map #(hash-map :cursor %1 :id %2 :cursor-on (= %3 %4) :track-on %5)
                                            (get cursor "beats")
                                            (repeat id)
                                            (repeat (first beat-cursors))
                                            (range)
                                            (repeat (get cursor "on")))]
                           (om/build-all cell-view cursors {:state state})))))
                (dom/td #js {:style #js {:color "#999" :paddingRight "10px"}}
                        (om/build track-editor {:cursor cursor :id id :delete-ch delete-ch
                                                :set-state-ch (:set-state-ch state)}))
                (dom/td nil
                        (when (= "grid" (get cursor "type"))
                          (let [id (get cursor "grid-id")
                                sub-state {:set-state-ch (:set-state-ch state)
                                           :get-state-ch (:get-state-ch state)}]
                            (om/build grid-view {:id id :beat-cursors (second beat-cursors)} {:state sub-state})))))))))

(defn grid-editor [{:keys [name id cursor set-state-ch], :as inputs} owner]
  (reify
    om/IInitState
    (init-state [_]
      {:state :init
       :bpc (get cursor "bpc")})
    om/IRenderState
    (render-state [_ state]
      (let [transition #(om/update-state! owner (fn [s] (merge s %)))
            closer #(dom/span #js {:onClick (fn [] (transition {:state :init}))
                                   :style #js {:color (:link theme)}} %)
            double-width #(let [tracks (get cursor "tracks")
                                doubled-tracks (map (fn [t]
                                                      (let [beats (get t "beats")]
                                                        (assoc t "beats"
                                                               (take (* 2 (count beats))
                                                                     (cycle beats)))))
                                                    tracks)]
                            (om/transact! cursor (fn [c] (assoc cursor "tracks" (clj->js doubled-tracks))))
                            (go (>! set-state-ch id)))
            half-width #(let [tracks (get cursor "tracks")
                              halved-tracks (map (fn [t]
                                                   (let [beats (get t "beats")]
                                                     (assoc t "beats"
                                                            (take (max (int (/ (count beats) 2)) 1) beats))))
                                                 tracks)]
                          (om/transact! cursor (fn [c] (assoc cursor "tracks" (clj->js halved-tracks))))
                          (go (>! set-state-ch id)))
            bpc-list (om/observe owner (bpc-values))
            double-res #(when-not (= (first bpc-list) (get cursor "bpc"))
                          (let [current-bpc (get cursor "bpc")
                                bpc (last (take-while (fn [v] (not (= current-bpc v))) bpc-list))
                                tracks (get cursor "tracks")
                                doubled-tracks (map (fn [t]
                                                      (let [beats (get t "beats")
                                                            doubled-beats (interleave (map (fn [b] (vector (* 2 (first b)))) beats)
                                                                                      (repeat [0]))]
                                                        (assoc t "beats" doubled-beats)))
                                                    tracks)]
                            (om/transact! cursor (fn [c] (assoc c
                                                                "tracks" (clj->js doubled-tracks)
                                                                "bpc" bpc)))
                            (go (>! set-state-ch id))))
            half-res #(when-not (= (last bpc-list) (get cursor "bpc"))
                        (let [current-bpc (get cursor "bpc")
                              bpc (second (drop-while (fn [v] (not (= current-bpc v))) bpc-list))
                              tracks (get cursor "tracks")
                              halved-tracks (map (fn [t]
                                                 (let [beats (get t "beats")
                                                       halved-beats (filter some?
                                                                            (map-indexed (fn [i b] (if (even? i) (vector (if (= 0 (first b)) 0
                                                                                                                             (max (/ (first b) 2) 1))) nil))
                                                                                         beats))]
                                                   (assoc t "beats" halved-beats)))
                                               tracks)]
                          (om/transact! cursor (fn [c] (assoc c
                                                              "tracks" (clj->js halved-tracks)
                                                              "bpc" bpc)))
                          (go (>! set-state-ch id))))]
        (condp = (:state state)
          :init (dom/table nil
                           (dom/tbody nil
                                      (dom/tr nil
                                              (dom/td nil (str name " "))
                                              (dom/td #js {:onClick #(transition {:state :open})
                                                           :style #js {:color (:link theme)}}
                                                      "{..}"))))
          :open (dom/table nil
                           (dom/tbody nil
                                      (dom/tr nil
                                              (dom/td nil (closer "{"))
                                              (dom/td nil " name: ")
                                              (dom/td nil (om/build name-editor inputs))
                                              (dom/td nil (str " (" id ")"))
                                              (dom/td nil " ")
                                              (dom/td nil " bpc: ")
                                              (dom/td nil (om/build bpc-editor inputs))
                                              (dom/td nil " ")
                                              (dom/td nil " width: ")
                                              (dom/td #js {:onClick double-width
                                                           :style #js {:color (:link theme)}} "+")
                                              (dom/td nil "/")
                                              (dom/td #js {:onClick half-width
                                                           :style #js {:color (:link theme)}} "-")
                                              (dom/td nil " ")
                                              (dom/td nil " resolution: ")
                                              (dom/td #js {:onClick double-res
                                                           :style #js {:color (:link theme)}} "+")
                                              (dom/td nil "/")
                                              (dom/td #js {:onClick half-res
                                                           :style #js {:color (:link theme)}} "-")
                                              (dom/td nil " }")))))))))

(defn grid-view [{:keys [id beat-cursors]} owner]
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
      (let [cursor (get (om/observe owner (grids)) id)
            name (get cursor "name")]
        (dom/table nil
                   (dom/tbody nil
                              (dom/td #js {:onClick #(om/set-state! owner :grid-expanded
                                                                    (not (:grid-expanded state)))
                                           :style #js {:background (:link-bar theme)
                                                       :color (:link theme)}}
                                      (if (:grid-expanded state) " - " " + "))
                              (dom/td #js {:style #js {:padding "0 0 0 10px"}}
                                      (if-not (:grid-expanded state)
                                        (dom/div style-grey name)
                                        (dom/div nil 
                                                 (dom/div style-grey
                                                        (om/build grid-editor {:name name
                                                                               :id id
                                                                               :set-state-ch (:set-state-ch state)
                                                                               :cursor cursor}))
                                                 (dom/table nil
                                                            (apply dom/tbody nil
                                                                   (let [cursors (map #(hash-map :cursor %1 :track-id (get %1 "id")
                                                                                                 :id %2 :delete-ch %3 :beat-cursors %4)
                                                                                      (get cursor "tracks")
                                                                                      (repeat id)
                                                                                      (repeat (:delete-ch state))
                                                                                      (if (nil? beat-cursors) (repeat nil) beat-cursors))]
                                                                     (om/build-all track-view cursors {:state state :key :track-id}))))
                                                 (when (> (config "MaxTrackCount") (count (get cursor "tracks")))
                                                   (om/build track-builder {:cursor (get cursor "tracks")
                                                                            :id id
                                                                            :set-state-ch (:set-state-ch state)})))))))))))

(defn error-view [cursor]
  (reify
    om/IRender
    (render [_]
      (dom/div #js {:style #js {:color "#FF0000"}}
               (apply dom/ul nil
                      (map (partial dom/li nil) cursor))))))

(defn button [{:keys [text click-chan]} owner]
  (reify
    om/IInitState
    (init-state [_]
      {:id (new-id)})
    om/IRenderState
    (render-state [_ state]
      (dom/div #js {:id (:id state)}))
    om/IDidMount
    (did-mount [_]
      (let [node (om/get-node owner)
            b (Button. text (.getInstance FlatButtonRenderer))]
        (.render b node)
        (gevents/listen b Component.EventType.ACTION
                        #(go (>! click-chan true)))))))

(defn hive-view [{:keys [cursor save-state-ch load-state-ch]}]
  (reify
    om/IRender
    (render [_]
      (dom/div #js {:style #js {:float "right"}}
               (om/build button {:text "Save state"
                                 :click-chan save-state-ch})
               (om/build button {:text "Load state"
                                 :click-chan load-state-ch})))))

(defn audio-view [_ owner]
  (reify
    om/IInitState
    (init-state [_]
      (let [play-ch (chan)
            stop-ch (chan)]
        (go-loop []
          (<! play-ch)
          (.play (. js/document (getElementById "audio")))
          (om/update-state! owner #(merge % {:state :play}))
          (recur))
        (go-loop []
          (<! stop-ch)
          (.pause (. js/document (getElementById "audio")))
          (om/update-state! owner #(merge % {:state :stop}))
          (recur))
        {:state :play
         :play-ch play-ch
         :stop-ch stop-ch}))
    om/IRenderState
    (render-state [_ state]
      (dom/div #js {:style #js {:float "right"}}
               (dom/audio #js {:id "audio"
                               :autoplay true}
                          (dom/source #js {:src (str "http://" js/window.location.hostname
                                                     ":" (config "UiAudioPort") "/hivejam")
                                           :type "audio/mpeg"}))
               (if (= :play (:state state))
                 (om/build button {:text "stop"
                                   :click-chan (:stop-ch state)})
                 (om/build button {:text "play"
                                   :click-chan (:play-ch state)}))))
    om/IDidMount
    (did-mount [_]
      (let [play-ch (:play-ch (om/get-state owner))]
        (go (>! play-ch true))))))

(defn console-view [cursor]
  (reify
    om/IRender
    (render [_]
      (dom/div #js {:style #js {:color (:foreground theme)}}
               (apply dom/ul nil
                      (map (partial dom/li nil) cursor))))))

(defn app-view [cursor _]
  (reify
    om/IInitState
    (init-state [_]
      (let [set-state-ch (chan)
            get-state-ch (chan)
            save-state-ch (chan)
            load-state-ch (chan)]
        (go
          (let [addr (str "ws://" (config "UiExternalIp") ":" (config "UiBridgePort") "/oscbridge")
                {:keys [ws-channel error]} (<! (ws-ch addr {:format :json}))]
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
              (<! save-state-ch)
              (let [request #js {:Address "/save-state"}]
                (go (>! ws-channel request)))
              (recur))
            (go-loop []
              (<! load-state-ch)
              (let [request #js {:Address "/load-state"}]
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
                    (om/transact! cursor :samples #(sort (into % samples))))
                  (= "/synths" (get message "Address"))
                  (let [synths (js->clj (js/JSON.parse (first params)))]
                    (om/transact! cursor :synths #(sort (into % synths))))
                  (= "/errors" (get message "Address"))
                  (let [errors (js->clj (js/JSON.parse (first params)))]
                    (om/update! cursor :errors errors))
                  (= "/console" (get message "Address"))
                  (let [console (js->clj (js/JSON.parse (first params)))]
                    (om/update! cursor :console console))
                  (= "/cursors" (get message "Address"))
                  (let [beat-cursors (js->clj (js/JSON.parse (first params)))]
                    (om/update! cursor :beat-cursors beat-cursors)))
                (when message
                  (recur))))))
        {:set-state-ch set-state-ch
         :get-state-ch get-state-ch
         :save-state-ch save-state-ch
         :load-state-ch load-state-ch
         :grid-expanded true}))
    om/IRenderState
    (render-state [_ state]
      (dom/div #js {:style #js {:fontFamily "monospace"
                                :background (:background theme)}}
               (om/build error-view (:errors cursor))
               (om/build hive-view {:cursor (:hive cursor)
                                    :save-state-ch (:save-state-ch state)
                                    :load-state-ch (:load-state-ch state)})
               (om/build audio-view {})
               (om/build grid-view {:id "root" :beat-cursors (:beat-cursors cursor)} {:state state})
               (om/build console-view (:console cursor))))))

(om/root app-view app-state
  {:target (. js/document (getElementById "app"))})
