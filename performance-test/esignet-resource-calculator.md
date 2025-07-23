# eSignet Resource Calculator - Mock IDA

This Resource Calculator is designed to help estimate the CPU, RAM, and Storage required for deploying the eSignet system in a given country or environment. The calculator leverages key assumptions, performance data, and buffer considerations to provide a comprehensive and scalable resource plan.

---

## 1. Overview

The **eSignet Resource Calculator** is intended to guide the teams in planning and provisioning resources for eSignet deployments. The data is based on:
- Country-specific population and usage metrics.
- Performance benchmarks from the eSignet team.
- Calculations for necessary buffers (monitoring, logging, Kubernetes overhead, etc.).

> **Legend:**
> - Blue: Data added by country
> - Green: Data added by performance team
> - Orange: Data calculated as per country input

---

## 2. Data Inputs

### To Be Provided by Country

| Assumptions                                                                    | Example Value  |
|--------------------------------------------------------------------------------|:--------------:|
| Total Population Having Registered National ID                                 | 4,000,000      |
| Percentage of Population using National ID for authentication                  | 50%            |
| Percentage of Population using National ID for authentication during peak hour | 50%  |

These values should be customized for your country's context.

---

## 3. Population based on the data provided

- **Total Population using National ID for authentication:**  
  `Registered Population × % Using for Authentication`  
  _Example:_ 4,000,000 × 50% = 2,000,000

- **Total Population using National ID for authentication during peak hour:**  
  `Population using for Authentication × % During Peak Hour`  
  _Example:_ 2,000,000 × 50% = 1,000,000

- **Expected Peak Hour TPS (Transactions Per Second):**  
  _Example:_ 278

---

## 4. Total TPS and Resources Used during eSignet Performance execution
This section is about the performance baselines considerations for 100TPS performance run. 

i.e for the current resource calculator it is 106 virtual users and 1 relying party completing 25000 OIDC flows, matching the expected peak hour TPS of 278 with a factor of 3.

---

## 5. Computations for the total resources required
To support a workload of 100 transactions per second (TPS) and accommodate peak hour demands, a comprehensive service-wise resource breakdown is shown in the report.

To ensure system reliability and operational readiness, buffer allocations were added to 
Monitoring, Logging, and Alerts, Kubernetes Infrastructure, System Buffer.

Combining all components, the total resources required ensuring robust performance and scalability under peak conditions are shown.

---

## 6. Storage
The Postgres DB size is computed considering, 1 user per relying party consumes 2.5KB of size to store user consent for total OIDC flows and factor to match peak hour TPS (OIDC flows x 2.5 KB x factor) 

Maximum memory recorded in redis cache for total OIDC flows along with 20% buffer is considered for redis cache computations.

Logs size is computed considering logs generated for total OIDC flows and calculated per OIDC flow.

Startup & Liveness Logs is the logs generated while bringing up the esignet service.

> **Note:** Additional storage may be required for infra logging and system buffer. The calculator provides estimates, but actual sizing should be validated in production or enterprise environments.

---

## 7. Usage Instructions

1. **Input Data:**  
   Update the input assumptions according to your country's statistics.

2. **Review Calculations:**  
   The calculator will automatically compute the required resources based on your inputs.

3. **Interpret Results:**  
   Use the computed vCPU, RAM, and storage requirements to plan your cloud or on-premise infrastructure.

4. **Buffer Appropriately:**  
   The calculator includes buffer recommendations, but you may adjust them based on your operational policies.

---