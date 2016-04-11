import time
import socket
import csv
def collect_metric(name, value, timestamp):
    sock = socket.socket()
    sock.connect( ("80.156.222.17", 2003) )
    print "%s %d %d\n" % (name, value, timestamp)
    sock.send("%s %d %d\n" % (name, value, timestamp))
    sock.close()


with open('created_dataset.csv', 'rb') as csvfile:
    spamreader = csv.reader(csvfile, delimiter=' ', quotechar='|')
    for row in spamreader:
        strtosend = row[0].split(',')
        collect_metric("autoscale.test",int(strtosend[2]),long(strtosend[4]))
