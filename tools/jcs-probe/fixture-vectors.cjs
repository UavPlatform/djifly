// Independent Node/V8 oracle for SYNTHETIC test data only. NOT a production identity schema.
// RFC8785 Appendix A shape, with only well-formed strings + safe integers in these fixtures.
const {createHash}=require('node:crypto');
function jcs(x) {
  if (Array.isArray(x)) return '['+x.map(jcs).join(',')+']';
  if(x!==null && typeof x==='object') return '{'+Object.keys(x).sort().map(k=>JSON.stringify(k)+':'+jcs(x[k])).join(',')+'}';
  return JSON.stringify(x);
}
const sha=(...chunks)=>createHash('sha256').update(Buffer.concat(chunks)).digest('hex');
const utf8=x=>Buffer.from(x,'utf8');
// Deliberately fake identity: no field-set, serial-source or assignment rule is frozen here.
const identity={fixtureIdentity:'not-a-production-chain'};
const h0=sha(utf8('UAV-EVENT-ANCHOR-v1\n'),utf8(jcs(identity)));
const body={schemaVersion:1,hashVersion:1,orderId:'fixture-order',recordingSourceId:'00000000-0000-4000-8000-000000000001',serialNumber:'FIXTURE_ONLY',serialSource:'FIXTURE_ONLY',model:'FIXTURE_ONLY',chainId:'00000000-0000-4000-8000-000000000002',packetId:'00000000-0000-4000-8000-000000000003',prevPacketId:null,height:1,prevChainHash:h0,events:[{eventId:'00000000-0000-4000-8000-000000000004',capturedAt:1794039540000,type:'FIXTURE_ONLY',payload:{testValue:7}}]};
const c=jcs(body);
const h1=sha(utf8('UAV-EVENT-PACKET-v1\n'),Buffer.from(h0,'hex'),utf8(c));
const full=jcs({...body,chainHash:h1});
console.log(JSON.stringify({identity:jcs(identity),h0,c,h1,full,contentDigest:sha(utf8(full))},null,2));
