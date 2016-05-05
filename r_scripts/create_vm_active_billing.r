Sys.setlocale("LC_TIME", "C");
library(lubridate);
library(TTR);

BASE = '/Users/subramanya/Documents/workspace/elasticapps/logs/'
billing_header = c("UnixTimeStamp","DateTime","ActiveSessions","VmDemand","VmActive","VmBilled");
vm_demand_available_billing = read.csv(paste(BASE,"demand_available_billing_dataset.csv", sep=""),  sep="," , header=F);
colnames(vm_demand_available_billing)  <- billing_header
vm_demand_available_billing$StampToPosxTime = as.POSIXct(vm_demand_available_billing$UnixTimeStamp, origin="1970-01-01")

