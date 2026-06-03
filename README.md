# Legal Petition Assistant

Android mobile application prototype for collecting legal case details through a
chat interface and generating a draft court petition.

## Features

- Select one or more legal case categories, such as civil, criminal, family,
  consumer, property, employment, rent/tenancy, tax, or other.
- Guided chat flow collects party details, court/jurisdiction, facts, relief
  requested, supporting documents, and urgency/limitation details.
- Configurable free chat window. The default is 5 minutes.
- Paid chat plans:
  - Rs. 100 for 10 minutes
  - Rs. 150 for 20 minutes
  - Rs. 250 for 50 minutes
- Petition draft generation with copy and share actions.

## Configuration

Edit `app/src/main/res/values/config.xml`:

- `free_chat_duration_seconds` controls the free chat window.
- `legal_case_categories` controls available categories.
- `payment_plan_prices_rupees` and `payment_plan_durations_minutes` define the
  paid chat plans. Keep both arrays in the same order.

## Build

Open the project in Android Studio, let it sync Gradle, and run the `app`
configuration on an emulator or Android device.

The project uses Android Gradle Plugin 9.2.0 and targets SDK 36.

## Legal note

The generated petition is a draft based on user-provided details. It should be
reviewed by a qualified legal professional before filing in court.
