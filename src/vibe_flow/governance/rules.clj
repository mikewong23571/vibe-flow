(ns vibe-flow.governance.rules)

(def governed-roots
  ["src" "test"])

(def required-project-paths
  ["deps.edn"
   ".pre-commit-config.yaml"
   "README.md"
   "docs/design.md"
   "docs/architecture.md"
   "docs/governance.md"
   "src"
   "test"
   "resources"
   "dev"
   "resources/vibe_flow/governance/module_manifest.edn"])

(def pre-commit-entry
  "clojure -M:governance")

(def machine-governed-layers
  #{:support-substrate
    :target-substrate
    :state-persistence
    :definition-interpretation
    :model-management
    :runtime-integration
    :workflow-control
    :product-surface
    :governance
    :test})

(def allowed-layer-dependencies
  {:support-substrate #{}
   :target-substrate #{:support-substrate}
   :state-persistence #{:support-substrate :target-substrate}
   :definition-interpretation #{:support-substrate :target-substrate}
   :model-management #{:support-substrate
                       :target-substrate
                       :state-persistence
                       :definition-interpretation}
   :runtime-integration #{:support-substrate
                          :target-substrate
                          :state-persistence
                          :definition-interpretation}
   :workflow-control #{:support-substrate
                       :target-substrate
                       :state-persistence
                       :definition-interpretation
                       :model-management
                       :runtime-integration}
   :product-surface #{:support-substrate
                      :target-substrate
                      :state-persistence
                      :definition-interpretation
                      :model-management
                      :runtime-integration
                      :workflow-control}
   :governance #{:support-substrate :governance}
   :test machine-governed-layers})

(def file-line-thresholds
  {:warn 300
   :error 400})

(def directory-entry-thresholds
  {:warn 7
   :error 11})

(def empty-directory-whitelist
  #{})

(def markdown-document-roots
  ["docs/"
   "spikes/"])

(def root-markdown-document-whitelist
  #{"README.md"})

(def anywhere-markdown-document-names
  #{"AGENTS.md"})

(def product-cli-governance-path
  "src/vibe_flow/system.clj")

(def product-cli-governance-required-defns
  ["governed-product-cli-design-doc-path"
   "governed-cli-provider-whitelist"
   "governed-cli-registry-contract"
   "load-governed-cli-registry!"
   "dispatch-governed-cli-command!"
   "governed-cli-command-whitelist"])

(def rules
  {:project-layout
   {:intent "Keep a standard Clojure project skeleton so code, tooling, and governance all land in predictable places."
    :guidance "Fix the repo root structure by restoring missing formal project paths in the root project boundary."}

   :pre-commit
   {:intent "Enforce governance automatically before code lands, instead of relying on manual discipline."
    :guidance "Fix .pre-commit-config.yaml in the repo root so it invokes the governance CLI through the Clojure toolchain."}

   :module-manifest
   {:intent "Make every formal module explicitly classified before it grows, instead of letting structure drift silently."
    :guidance "Register the namespace in resources/vibe_flow/governance/module_manifest.edn and keep its metadata aligned with the file."}

   :module-contract
   {:intent "Keep volatility, complexity, and module role explicit so high-change and complex modules stay intentionally governed."
    :guidance "Fix the manifest entry for the flagged namespace by adding the required module metadata, split axis, or stability role in the governance manifest."}

   :product-cli-governance
   {:intent "Keep the formal product CLI intentionally governed instead of letting commands grow ad hoc in the routing surface."
    :guidance "Define the governed CLI whitelist/resource-family functions in src/vibe_flow/system.clj before expanding the product command surface."}

   :namespace-match
   {:intent "Keep namespace, directory layout, and source lookup aligned so navigation and tooling remain reliable."
    :guidance "Fix the namespace declaration or move the file under src/test so the namespace matches the filesystem path."}

   :layer-dependency
   {:intent "Keep architectural dependencies flowing downward so low-level modules do not absorb high-level control logic."
    :guidance "Move the dependency to a lower layer, extract a stable contract in a lower namespace, or relocate the calling code to the correct architectural layer."}

   :sample-boundary
   {:intent "Keep sample and debug logic out of the formal source tree so the product codebase stays clean."
    :guidance "Move sample/debug code out of src/ into a spike/demo area, or reclassify the code into a formal product layer if it belongs in the shipped system."}

   :file-length
   {:intent "Keep single-file complexity bounded so modules stay readable and refactorable."
    :guidance "Split responsibilities inside the flagged source file, or extract subordinate helpers into a nearby namespace in the same source area."}

   :empty-directory
   {:intent "Keep the governed source tree structurally intentional instead of preserving placeholder directories with no owned content."
    :guidance "Remove the empty directory, move real owned content into it, or explicitly whitelist the path in the governance rules if the exception is intentional."}

   :directory-size
   {:intent "Keep directory fan-out bounded so module families stay discoverable and extensible."
    :guidance "Split the flagged directory into clearer subdirectories, or move a distinct module family into a nearby namespace subtree."}

   :markdown-location
   {:intent "Keep Markdown documents in governed document areas instead of scattering planning notes and decisions across the repository."
    :guidance "Move the Markdown file under docs/ for formal project documents or spikes/ for historical exploration, unless it is one of the explicitly whitelisted root entry documents."}})

(defn rule-metadata [rule-id]
  (assoc (get rules rule-id) :id rule-id))
