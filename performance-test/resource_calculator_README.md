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
> - Grey: Data added by performance team
> - Gold: Data calculated as per country input

---

## 2. Data Inputs

### To Be Provided by Country

| Assumptions                                                                    | Example Value  |
|--------------------------------------------------------------------------------|:--------------:|
| Total Population Having Registered National ID                                 | 105,000,000      |
| Percentage of Population using National ID for authentication                  | 50%            |
| Percentage of Population using National ID for authentication during peak hour | 2%  |

These values should be customized for your country's context.

---

## 3. Population based on the data provided

- **Total Population using National ID for authentication:**  
  `Registered Population × % Using for Authentication`  
  _Example:_ 105,000,000 × 50% = 52,500,000

- **Total Population using National ID for authentication during peak hour:**  
  `Population using for Authentication × % During Peak Hour`  
  _Example:_ 52,500,000 × 2% = 1,050,000

- **Expected Peak Hour TPS (Transactions Per Second):**  
  `Total Population using National ID for authentication during peak hour/3600`  
  _Example:_ 1,050,000 / 3600 = 291.67 = 292(approx.)

---

## 4. Total TPS and Resources Utilized During eSignet Performance Testing
This section outlines the performance baseline considerations derived from the 50 TPS performance test. Due to current infrastructure and resource constraints, it was not feasible to execute testing at the projected peak load of 292 TPS. Therefore, resource requirements for peak-load scenarios are estimated through extrapolation based on the results obtained at 50 TPS.

As the testing was conducted only at 50 TPS, the resource requirements for 292 TPS are estimated by applying an approximate scaling factor of 6× to the observed resource consumption.  

---

## 5. Computations for the total resources required
To support a workload of 50 transactions per second (TPS) and accommodate peak hour demands, a comprehensive service-wise resource breakdown is shown in the report.

To ensure system reliability and operational readiness, buffer allocations were added to 
Monitoring, Logging, and Alerts, Kubernetes Infrastructure, System Buffer.

Combining all components, the total resources required ensuring robust performance and scalability under peak conditions are shown.

---

## 6. Storage
The Postgres DB size is computed considering, 1 user per relying party consumes 0.6KB of size to store user consent for total OIDC flows and factor to match peak hour TPS (OIDC flows x 0.6 KB x factor) 

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