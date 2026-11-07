# Remaining Work

This file tracks deferred implementation work and review findings. Design and
implementation details live in `doc/`.

- Create a nice Icon and use it for the window icon

## Accessing nodes
Add `var ref=useRef()` hook; Extend style with `ref(ref)`. `ref.current` contains the node after rendering. After layout,
position can be obtained from node.


## Menu/Popover/Modal Handling
The UI provides mechanisms to add remove nodes to the end of the root node.
`showModal(close->{})`. The lambda runs in the context of the root. Modals are added to the end and can be removed from the list.
Place using "display: absolute";

Open question: need to get width/height of popup component before display. Idea: add `useLayoutEffect`, measure node, adjust placement.