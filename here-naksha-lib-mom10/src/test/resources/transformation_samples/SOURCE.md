### Samples source & layout

Samples were prepared based on the payload found in the [shift-mom repository](https://main.gitlab.in.here.com/ce/mom/shiftmom/).
Features in MOM format prior to MOM 10.0.0 version are called `original.json` and come from version `8.19.1` source: [8.19.1 branch](https://main.gitlab.in.here.com/ce/mom/shiftmom/-/tree/8.19.1/).
Features in "semi-MOM 10.0.0 format" version are called `transformed.json`, source [10.0.0 branch](https://main.gitlab.in.here.com/ce/mom/shiftmom/-/tree/10.0.0/).
Keep in mind that our output format is not real MOM-10, out transformation retain old namespaces and simply add MOM-10 compatible ones next to them (ie `meta`).

#### Topology Point Anchor
- test dir: [topology_point_anchor](topology_point_anchor)
- MOM 8.19.1: https://main.gitlab.in.here.com/ce/mom/shiftmom/-/blob/8.19.1/typescript/samples/mom/feature/anchorSamplePoint.ts 
- MOM 10.0.0: https://main.gitlab.in.here.com/ce/mom/shiftmom/-/blob/10.0.0/typescript/samples/mom/feature/anchorSamplePoint.ts

#### Topology Range Anchor
- test dir: [topology_range_anchor](topology_range_anchor)
- MOM 8.19.1: https://main.gitlab.in.here.com/ce/mom/shiftmom/-/blob/8.19.1/typescript/samples/mom/feature/anchorSampleRange.ts
- MOM 10.0.0: https://main.gitlab.in.here.com/ce/mom/shiftmom/-/blob/10.0.0/typescript/samples/mom/feature/anchorSampleRange.ts
 
#### Crosswalk
- test dir: [crosswalk](crosswalk)
- MOM 8.19.1: https://main.gitlab.in.here.com/ce/mom/shiftmom/-/blob/8.19.1/typescript/samples/mom/feature/crosswalkSample.ts
- MOM 10.0.0: https://main.gitlab.in.here.com/ce/mom/shiftmom/-/blob/10.0.0/typescript/samples/mom/feature/crosswalkSample.ts
 
#### Lane Marking
- test dir: [lane_marking](lane_marking)
- MOM 8.19.1: https://main.gitlab.in.here.com/ce/mom/shiftmom/-/blob/8.19.1/typescript/samples/mom/feature/laneMarkingWithConfidenceSample.ts
- MOM 10.0.0: https://main.gitlab.in.here.com/ce/mom/shiftmom/-/blob/10.0.0/typescript/samples/mom/feature/laneMarkingWithConfidenceSample.ts
