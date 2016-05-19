library(fpp)
library(forecast)
options(error=NULL)

getMotionPrediction=function (tseries,horizon) {
  currentIndex=length(tseries)
  avg=tseries[currentIndex];
  if(currentIndex>=3) {
    u=(tseries[currentIndex]-tseries[currentIndex-2])/2  
    a=(tseries[currentIndex]-2*tseries[currentIndex-1]+tseries[currentIndex-2])
  } else {
    a=0;
    u=0;
  }
  return (avg+u*horizon+0.5*a*horizon*horizon)
}

getArimaPrediction=function(tseries,horizon) {
  arimaFit=auto.arima(tseries)
  fmodel=forecast(arimaFit,h=horizon)
  return (fmodel$mean[1:horizon])
}

getExpPrediction=function(tseries,horizon) {
  #if(length(tseries)<7)
  #tseries=ts(c(tseries[1],tseries[1]-1,tseries[1]-2,tseries[1]-3,tseries[1],tseries[1]))
  
  #expFit=ets(tseries, model="ZZZ", damped=NULL, alpha=NULL, beta=NULL,
  #        gamma=NULL, phi=NULL, additive.only=FALSE, lambda=NULL,
  #       lower=c(rep(0.0001,3), 0.8), upper=c(rep(0.9999,3),0.98),
  #      opt.crit=c("lik","amse","mse","sigma","mae"), nmse=3,
  #    bounds=c("both","usual","admissible"),
  #  ic=c("aicc","aic","bic"), restrict=TRUE)
  #expFit=holt(tseries,exponential=TRUE,damped=TRUE)
  expFit=ses(tseries,alpha = 0.96,initial="optimal", h=1)
  fmodel=forecast(expFit,h=horizon)
  return (expFit$mean)
}


getNNetPrediction=function(tseries,horizon) {
  if(length(tseries)==1)
    tseries=ts(c(tseries[1],tseries[1]-1))
  
  nnetFit=nnetar(tseries,lambda=0)
  fmodel=forecast(nnetFit,h=horizon)
  return (fmodel$mean[1:horizon])
}

prediction=function (tsdata, doView=TRUE, horizon=1) {
  dseries=c();
  tseries=ts();     
  
  ArimaRE=c();
  ArimaAE=c()
  ArimaSE=c()
  ArimaSSE=c()
  ArimaSAE=c()
  Arimaresiduals=c();
  arimaSse=0;
  arimaSae=0;
  
  CurrentRE=c();
  CurrentAE=c()
  CurrentSE=c()
  CurrentSSE=c()
  CurrentSAE=c()
  Currentresiduals=c();
  currentSse=0;
  currenntSae=0;
  
  NNetRE=c();
  NNetAE=c()
  NNetSE=c()
  NNetSSE=c()
  NNetSAE=c()
  NNetresiduals=c();
  nnetSse=0;
  nnetSae=0;
  
  EnsembleRE=c();
  EnsembleAE=c()
  EnsembleSE=c()
  EnsembleSSE=c()
  EnsembleSAE=c()
  Ensembleresiduals=c();
  ensembletSse=0;
  ensembletSae=0;
  
  arimaPredicted=c();
  nnetPredicted=c();
  ensemblePredicted=c();
  currentPredicted=c();
  ensembleWindow=c();
  ensembleDataFrame = data.frame()
  
  currentPredicted[1]=tsdata[1];
  arimaPredicted[1]=tsdata[1];
  nnetPredicted[1]=tsdata[1]
  ensemblePredicted[1]=tsdata[1]
  alpha=1;beta=1;
  
  for (i in 1:length(tsdata)) {
    dseries=c(dseries,tsdata[i])
    tseries=ts(dseries)
    
    arimaWindow = getArimaPrediction(tseries, horizon)
    arimaPredicted[i+1] = arimaWindow[1]
    nnetWindow = getNNetPrediction(tseries, horizon)
    nnetPredicted[i+1] = nnetWindow[1]
    
    Arimaresiduals[i]=(arimaPredicted[i]-tseries[i])
    ArimaAE[i]=abs(Arimaresiduals[i])
    ArimaSE[i]= ArimaAE[i]*ArimaAE[i];
    arimaSse=arimaSse+ ArimaSE[i]
    arimaSae=arimaSae+ ArimaAE[i] 
    ArimaRE[i]=ArimaAE[i]/tseries[i]
    ArimaSSE[i]=arimaSse;
    ArimaSAE[i]=arimaSae;
    
    NNetresiduals[i]=(nnetPredicted[i]-tseries[i])
    NNetAE[i]=abs( NNetresiduals[i])
    NNetSE[i]=  NNetAE[i]* NNetAE[i];
    nnetSse=nnetSse+  NNetSE[i]
    nnetSae= nnetSae+  NNetAE[i] 
    NNetRE[i]= NNetAE[i]/tseries[i]
    NNetSSE[i]=nnetSse;
    NNetSAE[i]=nnetSae;
    
    if(i==1 || ArimaAE[i-1]==0 || NNetAE[i-1]==0) {
      alpha=1
      beta=1
    } else {
      if(NNetresiduals[i]*Arimaresiduals[i]<0) {
        alpha=NNetAE[i];
        beta=ArimaAE[i]             
      } else {
        if(ArimaAE[i]<NNetAE[i]) {
          alpha=1;
          beta=0 
        } else {
          alpha=0;
          beta=1; 
        }
      }
    }
    alpha = 1;
    beta = 0;
    ensemblePredicted[i+1]=((alpha*arimaPredicted[i+1]+beta*nnetPredicted[i+1])/(alpha+beta))
    currentPredicted[i+1]= getMotionPrediction(tseries, 1)
    
    ensembleWindow[1] = ensemblePredicted[i+1]
    for (j in 2:horizon) {
      ensembleWindow[j] = ((alpha*arimaWindow[j]+beta*nnetWindow[j])/(alpha+beta))
    }
    ensembleDataFrame = rbind(ensembleDataFrame, ensembleWindow)
    
    Ensembleresiduals[i]=(ensemblePredicted[i]-tseries[i])
    EnsembleAE[i]=abs( Ensembleresiduals[i])
    EnsembleSE[i]=  EnsembleAE[i]* EnsembleAE[i];
    ensembletSse=ensembletSse+  EnsembleSE[i]
    ensembletSae= ensembletSae+  EnsembleAE[i] 
    EnsembleRE[i]= EnsembleAE[i]/tseries[i]
    EnsembleSSE[i]=ensembletSse;
    EnsembleSAE[i]=ensembletSae;
    
    Currentresiduals[i]=(currentPredicted[i]-tseries[i])
    CurrentAE[i]=abs(Currentresiduals[i])
    CurrentSE[i]= CurrentAE[i]*CurrentAE[i];
    currentSse=currentSse+ CurrentSE[i]
    currenntSae=currenntSae+ CurrentAE[i] 
    CurrentRE[i]=CurrentAE[i]/tseries[i]
    CurrentSSE[i]=currentSse;
    CurrentSAE[i]=currenntSae;
  }
  
  if (doView) {
    ds=cbind(dseries,currentPredicted,arimaPredicted,nnetPredicted,ensemblePredicted,CurrentAE,ArimaAE,NNetAE,EnsembleAE,CurrentRE,ArimaRE,NNetRE,EnsembleRE)
    View(ds)
    pdf("plot.pdf")

    old.par <- par(mfrow=c(2,2 ))
    plot(ts(ds[,3],start=start(tsdata)),col=2,plot.type = "s",ylab="", main="Real data vs ARIMA prediction") 
    lines(ts(ds[,1],start=start(tsdata)),col=1)
    
    plot(ts(ds[,2],start=start(tsdata)),col=3,plot.type = "s",ylab="A", main="Real data vs Motion Equation")
    lines(ts(ds[,1],start=start(tsdata)),col=1)
    
    plot(ts(ds[,4],start=start(tsdata)),col=4,plot.type = "s",ylab="", main="Real data vs Neural Network Prediction")
    lines(ts(ds[,1],start=start(tsdata)),col=1)
    
    plot(ts(ds[,5],start=start(tsdata)),col=5,plot.type = "s",ylab="", main="Real data vs Ensemble Prediction")
    lines(ts(ds[,1],start=start(tsdata)),col=1)

		dev.off ();
  }
  return(ensembleDataFrame)
}

generatePredictions = function() {
  horizon = 15
  
  start = 1
  limit = 430
  #infile = "/Users/subramanya/Documents/workspace/AutoscaleAnalyser/datasets/cloud_traces/cpu1_47h.log"
  infile = "../simulation/data/lab.csv"
  outdir = "aws_rubis"
  datafile = paste(outdir, "actual.csv", sep="")
  outfile = paste(outdir, "predicted.csv", sep="")
  
  #file = commandArgs(TRUE)
  data = read.csv(file=infile, header=FALSE)[start:(start+limit-1),]
  write.table(data, file=datafile, quote=FALSE, col.names=FALSE, sep=",")
  
  output = prediction(ts(data), TRUE, horizon)
  write.table(output, file=outfile, quote=FALSE, col.names=FALSE, sep=",")
}

generatePredictions()

#predict(air)
#air, euretail,sunspotarea,oil,ausair,austourists(nnet)
