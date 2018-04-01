import java.util.ArrayList;
import java.util.Comparator;

/**
 * Huffman coding project.
 */
public class Huffman {
  private static final int MAX_SIZE = 256;
  private static final String MAGIC_NUMBER = "HF";

  private String inputFile;
  private String outputFile;
  private boolean verbose;
  // Size of Huffman tree when compressing.
  private int treeSize = 0;

  /** A cell in array of frequencies. */
  private class Cell {
    char data;
    int freq;

    Cell(char data) {
      this.data = data;
      freq = 0;
    }
  }

  /** Node in sorted linked list and in Huffman tree. */
  private class Node {
    char data;
    int freq;
    Node left = null;
    Node right = null;
    Node next = null;

    Node(char data, int freq) {
      this.data = data;
      this.freq = freq;
    }

    Node(int freq, Node left, Node right) {
      this.freq = freq;
      this.left = left;
      this.right = right;
    }

    boolean isLeaf() {
      return left == null && right == null;
    }
  }

  public Huffman(String inputFile, String outputFile, boolean verbose) {
    this.inputFile = inputFile;
    this.outputFile = outputFile;
    this.verbose = verbose;
  }

  public void compress(boolean force) {
    ArrayList<Cell> freqList = new ArrayList<Cell>(MAX_SIZE);
    // 0 frequency for all.
    for (int i = 0; i < MAX_SIZE; i++) {
      freqList.add(i, new Cell((char) i));
    }

    // Find frequencies for each character in the file.
    int numInputChars = 0;
    TextFile reader = new TextFile(inputFile, 'r');
    while(!reader.EndOfFile()) {
      char currentChar = reader.readChar();
      Cell cell = freqList.get((int) currentChar);
      cell.freq++;
      numInputChars++;
    }

    // Sort by frequencies.
    sortFreqList(freqList);

    // Print frequency of each character in input file.
    if (verbose) { 
      System.out.println("Frequency of each character in input file:");
      for (Cell cell: freqList) {
        if (cell.freq > 0) {
          System.out.println((int) cell.data + " frequency is " + cell.freq);
        }
      }
      System.out.print("\n");
    }

    // Create a linked list without 0 frequency.
    Node head = createLinkedList(freqList);
    // Build Huffman tree from sorted linked list.
    Node tree = createHuffmanTree(head);

    // Build lookup table of codes.
    ArrayList<String> huffmanTable = new ArrayList<>(MAX_SIZE);
    for (int i = 0; i < MAX_SIZE; i++) {
      huffmanTable.add("");
    }

    if (verbose) {
      System.out.println("The Huffman tree:");
    }
    treeSize = 0;
    preorderTraversal(tree, huffmanTable, "");

    // Print code for each ASCII.
    if (verbose) {
      System.out.println("\n\nThe Huffman codes for each character:");
      for (int i = 0; i < MAX_SIZE; i++) {
        String path = huffmanTable.get(i);
        if (!path.isEmpty()) {
          System.out.println(i + " code is " + path);
        }
      }
      System.out.print("\n");
    }

    // Compute size of both files.
    int originalSize = numInputChars * 8;
    int compressedSize = computeCompressedSize(freqList, huffmanTable);

    // Print size of input and output files.
    if (verbose) {
      System.out.println("Size of the uncompressed file is " + originalSize);
      System.out.println("Size of the compressed file is " + compressedSize + "\n");
    }

    // Compare sizes.
    Assert.notFalse(compressedSize < originalSize || force, "File was not compressed.");
    writeCompressedFile(reader, tree, huffmanTable);
  }

  /** Create Huffman tree from linked list and return tree root. */
  private Node createHuffmanTree(Node head) {
    while (head.next != null) {
      Node leftChild = head;
      Node rightChild = head.next;
      Node parent = new Node(leftChild.freq + rightChild.freq, leftChild, rightChild);

      // Add to the head of the linked list.
      parent.next = rightChild.next;
      head = parent;
      leftChild.next = null;
      rightChild.next = null;

      // Check if tree is complete.
      if (head.next == null) {
        return head;
      }

      // Find right place for parent.
      if (parent.freq > head.next.freq) {
        head = head.next;
        parent.next = null;
        Node prev = head;
        while (prev != null) {
          if (prev.next == null || parent.freq <= prev.next.freq) {
            parent.next = prev.next;
            prev.next = parent;
            break;
          }
          prev = prev.next;
        }
      }
    }
    return head;
  }
  
  /** Traverses nodes in preorder, computes tree size and fills in huffman table. */
  private void preorderTraversal(Node node, ArrayList<String> huffmanTable, String path) {
    if (node.isLeaf()) {
      treeSize += 9;
      huffmanTable.set((int) node.data, path);
      if (verbose) {
        System.out.print("0" + node.data);
      }
    } else {
      treeSize++;
      preorderTraversal(node.left, huffmanTable, path + "0");
      preorderTraversal(node.right, huffmanTable, path + "1");
      if (verbose) {
        System.out.print("1");
      }
    }
  }

  /** Prints tree to file in preorder. */
  private void printHuffmanTree(Node node, BinaryFile writer) {
    if (node.isLeaf()) {
      writer.writeBit(false);
      writer.writeChar(node.data);
    } else {
      writer.writeBit(true);
      printHuffmanTree(node.left, writer);
      printHuffmanTree(node.right, writer);
    }
  }

  /** Write compressed to output file. */
  private void writeCompressedFile(TextFile reader, Node tree, ArrayList<String> huffmanTable) {
    BinaryFile writer = new BinaryFile(outputFile, 'w');

    // Print HF and the tree.
    writer.writeChar(MAGIC_NUMBER.charAt(0));
    writer.writeChar(MAGIC_NUMBER.charAt(1));
    printHuffmanTree(tree, writer);

    reader.rewind();
    while (!reader.EndOfFile()) {
      char currentChar = reader.readChar();
      String path = huffmanTable.get((int) currentChar);
      for (int i = 0; i < path.length(); ++i) {
        boolean bit = path.charAt(i) == '0' ? false : true;
        writer.writeBit(bit);
      }
    }
    reader.close();
    writer.close();
  }

  /** Compute size of compressed file. */
  private int computeCompressedSize(ArrayList<Cell> freqList, ArrayList<String> huffmanTable) {
    int compressedSize = treeSize + 2 + 4;
    for (Cell cell: freqList) {
      if (cell.freq > 0) {
        String path = huffmanTable.get((int)cell.data);
        compressedSize += cell.freq * path.length();
      }
    }
    compressedSize = Math.round((float)compressedSize / 8) * 8;
    return compressedSize;
  }

  /** Create linked list from frequency array. */
  private Node createLinkedList(ArrayList<Cell> freqList) {
    Node head = null;
    Node prev = null;
    for (Cell cell : freqList) {
      // Do not include 0 frequencies.
      if (cell.freq > 0) {
        Node node = new Node(cell.data, cell.freq);
        if (head == null) {
          head = node;
        }
        if (prev != null) {
          prev.next = node;
        }
        prev = node;
      }
    }
    return head;
  }

  private void sortFreqList(ArrayList<Cell> freqList) {
    freqList.sort(new Comparator<Cell>() {
      @Override
      public int compare(Cell first, Cell second) {
        if (first.freq < second.freq) {
          return -1;
        }
        if (first.freq > second.freq) {
          return 1;
        }
        return 0;
      }
    });
  }

  public void uncompress() {
    BinaryFile reader = new BinaryFile(inputFile, 'r');

    // Check magic number.
    String magic = "";
    magic += reader.readChar();
    magic += reader.readChar();
    Assert.notFalse(MAGIC_NUMBER.equals(magic), "Magic does not match " +  magic);

    // Print Huffman tree.
    if (verbose) {
      System.out.println("The Huffman tree:");
    }
    Node tree = buildHuffmanTree(reader);
    if (verbose) {
      System.out.println("\n");
    }

    // Uncompress to output file.
    TextFile writer = new TextFile(outputFile, 'w');
    while (!reader.EndOfFile()) {
      Node current = tree;
      while (!current.isLeaf()) {
        boolean bit = reader.readBit();
        if (!bit) {
          current = current.left;
        } else {
          current = current.right;
        }
      }
      writer.writeChar(current.data);
    }
    reader.close();
    writer.close();
  }

  /** Build Huffman tree from file. */
  private Node buildHuffmanTree(BinaryFile reader) {
    Assert.notFalse(!reader.EndOfFile(), "Error: end of file.");
    boolean bit = reader.readBit();
    if (!bit) {
      char data = reader.readChar();
      Node node = new Node(data, 0);
      if (verbose) {
        System.out.print("0" + data);
      }
      return node;
    }
    Node leftNode = buildHuffmanTree(reader);
    Node rightNode = buildHuffmanTree(reader);
    if (verbose) {
      System.out.print("1");
    }
    return new Node(0, leftNode, rightNode);
  }

  public static void main(String[] args) {
    Assert.notFalse(args.length >= 3, "Wrong number of arguments.");
    String inputFile = args[args.length - 2];
    String outputFile = args[args.length - 1];
    Boolean compress = null;
    boolean verbose = false;
    boolean force = false;

    String arg = args[0];
    if (arg.equals("-c")) {
      compress = true;
    } else if (arg.equals("-u")) {
      compress = false;
    }
    Assert.notNull(compress, "Argument -c and -u not set.");

    // Check if has -v or -f.
    for (int i = 1; i < args.length - 2; ++i) {
      arg = args[i];
      if (arg.equals("-v")) {
        verbose = true;
      } else if (arg.equals("-f")) {
        force = true;
      }
    }

    Huffman huffman = new Huffman(inputFile, outputFile, verbose);
    if (compress) {
      huffman.compress(force);
    } else {
      huffman.uncompress();
    }
  }
}
