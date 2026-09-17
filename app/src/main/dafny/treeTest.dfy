module TreeTest {

  datatype Tree<T> =
      Node(left: Tree<T>, right: Tree<T>)
    | Leaf(value: T)
  {
    function  pointer(): Pointer<T> {
      Pointer(Root, this)
    }
  }

  // A location identifies a subtree by recording the path from the root.
  datatype Location<T> =
      Root
    | Left(parent: Location<T>, right: Tree<T>)
    | Right(parent: Location<T>, left: Tree<T>)
  {
    // Reconstruct the whole tree by placing t at this location.
    function  reconstruct(t: Tree<T>): Tree<T>
    {
      match this
      case Root =>
        t
      case Left(parent, right) =>
        parent.reconstruct(Node(t, right))
      case Right(parent, left) =>
        parent.reconstruct(Node(left, t))
    }

    // Construct the immediate parent subtree containing t.
    function  parentTree(t: Tree<T>): Tree<T>
    {
      match this
      case Root =>
        t
      case Left(_, right) =>
        Node(t, right)
      case Right(_, left) =>
        Node(left, t)
    }
  }

  // A pointer identifies a subtree together with its location in the tree.
  datatype Pointer<T> = Pointer(
    location: Location<T>,
    subtree: Tree<T>
  )
  {
    function left(): (result: Pointer<T>)
      requires subtree.Node?
      ensures result.reconstruct() == reconstruct()
    {
      Pointer(Left(location, subtree.right), subtree.left)
    }

    function right(): (result: Pointer<T>)
      requires subtree.Node?
      ensures result.reconstruct() == reconstruct()
    {
      Pointer(Right(location, subtree.left), subtree.right)
    }

    function parent(): (result:Pointer<T>)
      requires location != Root
      ensures result.reconstruct() == reconstruct()
    {
      Pointer(location.parent, location.parentTree(subtree))
    }

    function replace(newSubtree: Tree<T>): (result: Pointer<T>)
    {
      Pointer(location, newSubtree)
    }

    function reconstruct(): Tree<T>
    {
      location.reconstruct(subtree)
    }
  }

  method testOperations()
  {
    var tree := Node(Leaf(0), Leaf(1));
    var pointer := tree.pointer();

    assert pointer.left().subtree.value == 0;
    assert pointer.right().subtree.value == 1;

    assert pointer.left().reconstruct() == tree;
    assert pointer.left().parent() == pointer;

    assert pointer.right().replace(Leaf(2)).reconstruct()
        == Node(Leaf(0), Leaf(2));
  }

}
