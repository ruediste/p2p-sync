# Remaining Work

This file tracks deferred implementation work and review findings. Design and
implementation details live in `doc/`.

- use the jreact icon from the storybook documentation for the java storybook project/window icon

## Accessing nodes
Add `var ref=useRef()` hook; Extend style with `ref(ref)`. `ref.current` contains the node after rendering. After layout,
position can be obtained from node.


## Menu/Popover/Modal Handling
The UI provides mechanisms to add remove nodes to the end of the root node.
`showModal(close->{})`. The lambda runs in the context of the root. Modals are added to the end and can be removed from the list.
Place using "display: absolute";

need to get width/height of popup component before display. use `useLayoutEffect`, measure node, adjust placement.

## Icons
Use https://kordamp.org/ikonli/ for icons

## Styling
Likely remove the style rules. Extend inline styling with selectors/predicates, to replicate Tailwind CSS functionality.