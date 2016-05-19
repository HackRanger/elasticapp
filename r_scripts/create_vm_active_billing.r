Sys.setlocale("LC_TIME", "C");
library(lubridate);
library(TTR);
library(fpp)
library(forecast)


BASE = '/Users/subramanya/Documents/workspace/elasticapps/logs/'
billing_header = c("UnixTimeStamp","DateTime","ActiveSessions","VmDemand","VmActive","VmBilled");
vm_demand_available_billing = read.csv(paste(BASE,"demand_available_billing_dataset.csv", sep=""),  sep="," , header=F);
colnames(vm_demand_available_billing)  <- billing_header
vm_demand_available_billing$StampToPosxTime = as.POSIXct(vm_demand_available_billing$UnixTimeStamp, origin="1970-01-01")
library(zoo)
tsdata.userrequest = zoo(vm_demand_available_billing$ActiveSessions,vm_demand_available_billing$StampToPosxTime)
tsdata.vmdemand = zoo(vm_demand_available_billing$VmDemand,vm_demand_available_billing$StampToPosxTime)
tsdata.vmactive = zoo(vm_demand_available_billing$VmActive,vm_demand_available_billing$StampToPosxTime)
tsdata.vmbilling = zoo(vm_demand_available_billing$VmBilled,vm_demand_available_billing$StampToPosxTime)
tsdata.merged = merge(tsdata.userrequest,tsdata.vmdemand,tsdata.vmactive,tsdata.vmbilling)
tsdata.capacity = merge(tsdata.userrequest,tsdata.vmdemand*120,tsdata.vmactive*120,tsdata.vmbilling*120)

# Plots: User request with Min Max and Avg
tiff('userrequest.tiff', res=600, compression = "lzw", height=5, width=5, units="in")
opar <- par(no.readonly=TRUE)
plot(tsdata.userrequest, screens=1, pch=c(1),lty=c(3),main="Workload", xlab="Time", ylab="Number of user requests",col=c("red"),ylim=c(0, 750))
abline(h=c(max(vm_demand_available_billing$VmDemand*120)), lwd=1.5, lty=2, col="red")
abline(h=c(min(vm_demand_available_billing$VmDemand*120)), lwd=1.5, lty=2, col="blue")
abline(h=c(mean(vm_demand_available_billing$VmDemand*120)), lwd=1.5, lty=2, col="green")
legend("topright", inset=.02, title="Legend", c("Request","Minimum Request","Average Request", "Maximum Request"),lty=c(3,2,2,2), col=c("red","blue","green","red"),cex=0.75)
par(opar)
dev.off()

# Plots : Combined - Request, VM Demand, VM Active, VM Billing
lty=c("dotted", "solid")
tiff('tsmerged.tiff', res=600, compression = "lzw", height=5, width=5, units="in")
plot(tsdata.capacity[500:1440], screens=1, lty=lty, pch="3", main="Workload", xlab="Time (in Hours)", ylab="Number of user requests", col=c("red","blue","green","black"))
legend("bottomright", inset=.02, title="Legend", c("Request","VM Demand","VM Active", "VM Billing"),lty=c(3,2,2,2), col=c("red","blue","green","black"),cex=0.75)
dev.off()

# Plots: Seperate graphs - Request, VM Demand, VM Active, VM Billing
tiff('fourgraphs.tiff', res=600, compression = "lzw", height=5, width=10, units="in")
opar <- par(no.readonly=TRUE)
par(mfrow=c(2,2))
plot(tsdata.userrequest[500:1440], lty=c("dotted"), pch="3", main="Workload", xlab="Time (in Hours)", ylab="VM Count", col=c("red"))
plot(tsdata.vmdemand[500:1440], lty=c("dotted"), pch="3", main="VM Needed", xlab="Time (in Hours)", ylab="VM Count", col=c("blue"))
plot(tsdata.vmactive[500:1440], lty=c("dotted"), pch="3", main="VM Active", xlab="Time (in Hours)", ylab="VM Count", col=c("green"))
plot(tsdata.vmbilling[500:1440], lty=c("dotted"), pch="3", main="VM Billing", xlab="Time (in Hours)", ylab="VM Count", col=c("black"))
par(opar)
dev.off()

# Plots: Seperate graphs - ACF, PCF
tsdata.diffuserrequest = diff(tsdata.userrequest,differences=1)
tiff('timeseriesanalysis.tiff', res=600, compression = "lzw", height=5, width=10, units="in")
opar <- par(no.readonly=TRUE)
par(mfrow=c(2,2))
acf(tsdata.userrequest)
pacf(tsdata.userrequest)
acf(tsdata.diffuserrequest)
pacf(tsdata.diffuserrequest)
par(opar)
dev.off()

# Plots: ARIMA - Diagno
tiff('arimadiag.tiff', res=600, compression = "lzw", height=10, width=5, units="in")
opar <- par(no.readonly=TRUE)
# par(mfrow=c(2,2))
arimafit=auto.arima(tsdata.userrequest)
tsdiag(arimafit)
par(opar)
dev.off()

# Plots: ARIMA - Fit and Data
tiff('arimafit.tiff', res=600, compression = "lzw", height=5, width=5, units="in")
opar <- par(no.readonly=TRUE)
# par(mfrow=c(2,1))
arimafit=auto.arima(tsdata.userrequest[1:180])
plot(zoo(arimafit$x,vm_demand_available_billing$StampToPosxTime[1:180]),col="red",main="Workload Actual vs Fitted", xlab="Time (in Hours)", ylab="Number of user requests",)
lines(fitted(arimafit),col="blue")
par(opar)
dev.off()

# Plots: Cost
ASE = '/Users/subramanya/Documents/workspace/elasticapps/logs/'
billing_header = c("UnixTimeStamp","DateTime","ActiveSessions","VmDemand","VmActive","VmBilled");
vm_life_time = read.csv(paste(BASE,"demand_available_billing_dataset.csv", sep=""),  sep="," , header=F);
colnames(vm_life_time)  <- billing_header
vm_demand_available_billing$StampToPosxTime = as.POSIXct(vm_demand_available_billing$UnixTimeStamp, origin="1970-01-01")



prediction=function (tsdata, horizon=1) {
  tseries=tsdata;
  arimaPredicted=c();
  arimaFit=auto.arima(tseries)
  fmodel=forecast(arimaFit,h=horizon)
  arimaPredicted = fmodel$upper[1:horizon]
  return(arimaPredicted)
}

  generatePredictions = function() {
    BASE = '/Users/subramanya/Documents/workspace/elasticapps/logs/'
    billing_header = c("UnixTimeStamp","DateTime","ActiveSessions","VmDemand","VmActive","VmBilled");
    vm_demand_available_billing = read.csv(paste(BASE,"demand_available_billing_dataset.csv", sep=""),  sep="," , header=F);
    colnames(vm_demand_available_billing)  <- billing_header
    vm_demand_available_billing$StampToPosxTime = as.POSIXct(vm_demand_available_billing$UnixTimeStamp, origin="1970-01-01");
    library(zoo);
    tsdata.userrequest = zoo(vm_demand_available_billing$ActiveSessions,vm_demand_available_billing$StampToPosxTime);
    tsdata.vmdemand = zoo(vm_demand_available_billing$VmDemand,vm_demand_available_billing$StampToPosxTime);
    tsdata.vmactive = zoo(vm_demand_available_billing$VmActive,vm_demand_available_billing$StampToPosxTime);
    tsdata.vmbilling = zoo(vm_demand_available_billing$VmBilled,vm_demand_available_billing$StampToPosxTime);
    tsdata.merged = merge(tsdata.userrequest,tsdata.vmdemand,tsdata.vmactive,tsdata.vmbilling);
    tsdata.capacity = merge(tsdata.userrequest,tsdata.vmdemand*120,tsdata.vmactive*120,tsdata.vmbilling*120);
    horizon=15;
    start=1;
    arimaPredicted=c();
    arimaWindow = prediction(tsdata.userrequest[1], horizon);
    arimaPredicted[1] = arimaWindow;
    for (i in 1:180) {
      arimaWindow = prediction(tsdata.userrequest[1:i], horizon);
      arimaPredicted[i+1] = arimaWindow[1];
    }
    return(arimaPredicted)
  }

arimaPredicted = generatePredictions();
arimaPredicted;
PredictedData = zoo(arimaPredicted,vm_demand_available_billing$StampToPosxTime[1:181])

tiff('actualprediction.tiff', res=600, compression = "lzw", height=5, width=5, units="in")
opar <- par(no.readonly=TRUE)
#par(mfrow=c(2,1))
plot(tsdata.userrequest[1:181], main="Workload Actual vs Predicted", xlab="Time (in Minutes)", ylab="Number of user requests", col=c("black"))
lines(PredictedData,col=c("red"))
dev.off()
