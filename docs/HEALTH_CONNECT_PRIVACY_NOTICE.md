# GymTracker — Health Connect privacy notice

GymTracker can optionally read a small set of health data from Health Connect to show recovery context next to your training history.

If you choose to connect Health Connect, GymTracker asks only for read access to:

- sleep sessions and available sleep stages;
- resting heart rate;
- heart rate variability recorded specifically as RMSSD.

GymTracker uses these values only to display informational recovery context. They are not used to diagnose a medical condition, prescribe treatment, or automatically change workout loads, targets, previous-set references, routines, or progression.

Health Connect is optional. You can continue using the workout logger, History, Progress, Backup/Restore and CSV export without granting any health permission.

PR9 does not write health data to Health Connect, import Health Connect exercise sessions as GymTracker workouts, store Health Connect records in Room, include health context in GymTracker portable backups or CSV exports, send health values to a backend, or log health values to analytics/crash output.

The recovery screen reads a bounded time window on demand while you are using the app. GymTracker does not request background health access or extended-history access for this feature.

Health Connect data remains owned by its source applications and Health Connect. GymTracker keeps only the currently displayed normalized context in memory. Disconnecting Health Connect revokes GymTracker's Health Connect permissions and clears that in-memory context. Revoking permissions from Android/Health Connect is also supported and does not prevent normal GymTracker use.

If more than one application provides a supported record type, GymTracker keeps the sources separate rather than inventing a cross-source deduplication rule.

Before a Google Play production publication that requests these permissions, this same substantive privacy policy must be hosted at the public privacy-policy URL declared in Play Console and the required Health Apps/Data Safety declarations must be completed.
