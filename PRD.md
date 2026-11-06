# **Veste-AI: Product Requirements Document (Android MVP)**

# **1\. Executive Summary & Core Value Proposition**

## **Problem Statement**

Selecting daily outfits manually is frequently time-consuming, inefficient, and prone to decision fatigue. Users often struggle to visualize how separate garments look together on their specific body type without physically trying them on.

## **Product Vision**

An "on-demand" virtual dressing room mobile application built natively for Android devices. The platform enables users to digitize their physical wardrobe and instantly preview realistic clothing combinations directly on a baseline image of themselves.

## **Target Persona**

* **Primary Persona:** Everyday individuals seeking a stream-lined way to plan outfits, reduce morning prep time, and evaluate clothing combinations digitally.  
* **Key Needs:** Fast visualization, low-friction setup, simple cataloging of existing garments, and privacy-focused image handling.

---

# **2\. Technical Architecture & Constraints**

## **Platform & OS Requirements**

* **Target OS:** Native Android (Kotlin framework).  
* **Minimum SDK Version:** Android 8.0 (API Level 26 / Oreo) to ensure broad device compatibility while leveraging modern Android UI paradigms (Jetpack Compose recommended).

## **Storage Strategy**

* **Local-First Architecture:** To minimize early backend infrastructure expenses and simplify data privacy compliance during the MVP stage, all user media (baseline body photos and wardrobe catalog items) will be stored locally on the device’s internal storage.  
* **Database:** Local metadata (item tags, category mapping, lookbook history) will be managed using SQLite via Room Persistence Library.  
* **Scalability Path:** Architecture must decouple storage calls via a repository pattern to allow seamless integration with cloud infrastructure (e.g., AWS S3 and remote DBs) in post-MVP releases.

## **Virtual Try-On Integration**

* **API-Agnostic Engine:** The application UI acts as a generic client shell. It interfaces with external Virtual Try-On APIs (such as IDM-VTON or custom inference endpoints) using a standardized abstract networking layer.  
* **Business Logic Decoupling:** Hardware acceleration, rendering pipelines, or third-party AI backend swaps must not require architectural changes to the core client application.

```
+-------------------------------------------------------------------+

|                        Android Client UI                          |
|---|
+-------------------------------------------------------------------+
                                 |
                                 v
+-------------------------------------------------------------------+
|                     Abstract Inference Adapter                    |
+-------------------------------------------------------------------+
                                 |
                  +--------------+--------------+
                  |                             |
                  v                             v
+----------------------------------+ +------------------------------+
| Local Processing Pipeline (Mock) | | External API (e.g., IDM-VTON)|
+----------------------------------+ +------------------------------+
```

---

# **3\. Wardrobe Taxonomy & Data Model**

The application classifies garments into standard anatomical regions to facilitate accurate API payload construction and UI rendering.

| Category Identifier | Category Name | Description & Coverage | Priority Scope |
| :---- | :---- | :---- | :---- |
| `HEADWEAR` | Headwear | Hats, caps, beanies | Optional / Secondary (Post-MVP) |
| `TOP` | Upper Body | T-shirts, shirts, jackets, sweaters, coats | Core MVP |
| `BOTTOM` | Lower Body | Pants, jeans, shorts, skirts | Core MVP |
| `FULL_BODY` | Single-Piece | Jumpers, overalls, dresses (catenates upper & lower coverage) | Core MVP |
| `FOOTWEAR` | Footwear | Shoes, sneakers, boots, sandals | Core MVP |

## **Metadata Schema (Local Database)**

* **Garment ID:** String (UUID)  
* **Image URI:** String (Local File Path)  
* **Category Tag:** Enum (`HEADWEAR`, `TOP`, `BOTTOM`, `FULL_BODY`, `FOOTWEAR`)  
* **Created Date:** Timestamp  
* **Is Favorite:** Boolean

---

# **4\. Onboarding & Setup Workflow**

The onboarding workflow guides the user through initial permissions, baseline profile photo configuration, and populating their initial digital closet.

## **Step 1: Initial Launch & Permissions**

1. User launches the app for the first time.  
2. System displays a value proposition onboarding carousel.  
3. System requests runtime permissions:  
   * Camera Access (`CAMERA`)  
   * Read External Storage / Media Access (`READ_MEDIA_IMAGES`)  
4. Upon granting permissions, user transitions to Step 2\.

## **Step 2: Reference Body Photo Capture**

1. User is instructed to take or upload a full-body photograph standing in a neutral pose with clear lighting.  
2. An on-screen overlay provides framing guidelines (head-to-toe outline).  
3. The captured image is saved to local app storage as the default **Baseline Mannequin Image**.  
4. User can re-take or re-select this baseline photo at any point from settings.

## **Step 3: Initial Wardrobe Cataloging**

1. User is prompted to upload their first set of clothing items.  
2. Options provided: **Take Photo** or **Select from Gallery**.  
3. Image is cropped and background isolation guidelines are displayed to maximize input quality.

## **Step 4: Manual Metadata Selection**

1. For each newly added garment, the system presents a fast-tagging screen.  
2. User manually assigns the primary category: **Top**, **Bottom**, **Full-Body**, **Footwear**, or **Headwear**.  
3. Garment profile is finalized and persisted to the local SQLite database.

---

# **5\. User Interface & Feature Specifications**

## **5.1 Self-Service Virtual Fitting Room**

* **Canvas Display:** Displays the current Baseline Mannequin Image as the main background layer.  
* **Selection Drawers:** Bottom sheet tabbed view listing user garments filtered by category tags (Tops, Bottoms, Full-Body, Footwear).  
* **Selection Logic:**  
  * Selecting a `TOP` and `BOTTOM` highlights both items on the active canvas layer.  
  * Selecting a `FULL_BODY` item automatically deselects any currently active `TOP` or `BOTTOM` selection.  
* **Action Trigger:** A prominent **"Try On"** floating action button sends the payload (Baseline Image URI \+ Selected Garment URIs) to the active VTON service layer.  
* **Result Rendering:** Displays the generated composite image with options to **Save to Lookbook**, **Share**, or **Discard**.

## **5.2 Lookbook Feed**

* **Visual Grid:** A dedicated gallery view displaying historical saved outfit combinations.  
* **Card Details:** Each card displays the output rendering, associated item thumbnails used to generate the look, and creation date.  
* **Management Options:** Delete outfit, mark as favorite, or re-load constituent items directly back into the Virtual Fitting Room canvas.

---

# **6\. Future Enhancements (Post-MVP Roadmap)**

1. **Cloud Storage Integration (AWS S3)**  
   * Introduce user authentication (Firebase / Auth0).  
   * Cross-device synchronization for catalog items and saved lookbooks.  
2. **Automated Vision Metadata Extraction**  
   * Integrate an on-device machine learning model (e.g., TensorFlow Lite) to automatically detect garment categories and isolate backgrounds upon photo capture, bypassing manual categorization.  
3. **AI-Driven Daily Recommendations**  
   * Algorithm-driven outfit suggestions based on local weather context, user favorite history, and color compatibility matching.

