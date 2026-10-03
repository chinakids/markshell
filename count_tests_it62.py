import glob, xml.etree.ElementTree as ET
total=fails=errs=skips=0
for f in sorted(glob.glob('/Users/chinakids/Desktop/markshell/app/build/test-results/testDebugUnitTest/*.xml')):
    r=ET.parse(f).getroot()
    total+=int(r.get('tests',0)); fails+=int(r.get('failures',0)); errs+=int(r.get('errors',0)); skips+=int(r.get('skipped',0))
print("tests=%d failures=%d errors=%d skipped=%d" % (total,fails,errs,skips))
