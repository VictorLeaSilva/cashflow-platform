import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Leitura de {@code @Tag} em testes Java e matriz de rastreabilidade (T-003, RNF-008).
 *
 * <p>Executado com {@code java scripts/CheckTraceability.java <modo> ...} por
 * {@code scripts/check-traceability.sh} e {@code scripts/verify-task.sh}.
 *
 * <ul>
 *   <li>{@code tags ARQUIVO...}: uma linha por tag, {@code ID<TAB>arquivo<TAB>classe<TAB>método}
 *       (método vazio quando a tag está na classe);
 *   <li>{@code matrix RAIZ}: matriz em Markdown (stdout); os arquivos de teste vêm um por linha no
 *       stdin;
 *   <li>{@code report RAIZ}: lista os obrigatórios sem prova e os provados por evidência; exit 1 se
 *       faltar algum.
 * </ul>
 */
public final class CheckTraceability {

  private static final Set<String> SEMPRE_EVIDENCIA = Set.of("RNF-009", "RNF-010", "RNF-011");
  private static final Pattern TAG =
      Pattern.compile("@(?:[A-Za-z_]\\w*\\.)*Tag\\s*\\(\\s*(?:value\\s*=\\s*)?\"");
  private static final Pattern DECL_CLASSE =
      Pattern.compile("(?<![\\w.])(class|interface|enum|record)\\s+(\\w+)");

  private CheckTraceability() {}

  record Tag(String id, String arquivo, String classe, String metodo) {}

  record Tarefa(String id, Set<String> covers, Set<String> evidenceOnly) {}

  record Requisito(String id) implements Comparable<Requisito> {
    private static final Comparator<Requisito> ORDEM =
        Comparator.comparing((Requisito r) -> r.id().startsWith("RNF") ? 1 : 0)
            .thenComparingInt(r -> Integer.parseInt(r.id().substring(r.id().indexOf('-') + 1)));

    @Override
    public int compareTo(Requisito o) {
      return ORDEM.compare(this, o);
    }

    boolean obrigatorioComTag() {
      if (id.startsWith("RF-")) {
        return true;
      }
      int n = Integer.parseInt(id.substring(4));
      return (n >= 1 && n <= 8) || (n >= 12 && n <= 14);
    }
  }

  public static void main(String[] args) throws IOException {
    PrintStream out =
        new PrintStream(new FileOutputStream(FileDescriptor.out), false, StandardCharsets.UTF_8);
    if (args.length < 1) {
      uso();
      return;
    }
    switch (args[0]) {
      case "tags" -> {
        for (String arquivo : Arrays.asList(args).subList(1, args.length)) {
          for (Tag t : extrairTags(arquivo, Files.readString(Path.of(arquivo)))) {
            out.print(
                t.id() + "\t" + t.arquivo() + "\t" + t.classe() + "\t" + t.metodo() + "\n");
          }
        }
      }
      case "matrix", "report" -> {
        if (args.length != 2) {
          uso();
          return;
        }
        Analise a = analisar(Path.of(args[1]), lerStdin());
        if (args[0].equals("matrix")) {
          out.print(a.matriz());
        } else {
          out.print(a.relatorio());
          out.flush();
          System.exit(a.faltantes().isEmpty() ? 0 : 1);
        }
      }
      default -> uso();
    }
    out.flush();
  }

  private static void uso() {
    System.err.println("uso: CheckTraceability tags ARQUIVO... | matrix RAIZ | report RAIZ");
    System.exit(2);
  }

  private static List<String> lerStdin() throws IOException {
    String todo = new String(System.in.readAllBytes(), StandardCharsets.UTF_8);
    return todo.lines().map(String::strip).filter(l -> !l.isEmpty()).sorted().toList();
  }

  // ------------------------------------------------------------------ leitura de @Tag

  /** Conteúdo sem comentários ({@code visivel}) e sem comentários nem literais ({@code forma}). */
  record Fonte(char[] visivel, char[] forma) {}

  static Fonte limpar(String src) {
    char[] a = src.toCharArray();
    char[] b = a.clone();
    int n = a.length;
    int i = 0;
    while (i < n) {
      char c = a[i];
      if (c == '/' && i + 1 < n && a[i + 1] == '/') {
        while (i < n && a[i] != '\n') {
          a[i] = ' ';
          b[i++] = ' ';
        }
      } else if (c == '/' && i + 1 < n && a[i + 1] == '*') {
        int fim = src.indexOf("*/", i + 2);
        fim = fim < 0 ? n : fim + 2;
        for (; i < fim; i++) {
          if (a[i] != '\n') {
            a[i] = ' ';
            b[i] = ' ';
          }
        }
      } else if (c == '"' && src.startsWith("\"\"\"", i)) {
        i = pularLiteral(b, src, i + 3, "\"\"\"");
      } else if (c == '"') {
        i = pularLiteral(b, src, i + 1, "\"");
      } else if (c == '\'') {
        i = pularLiteral(b, src, i + 1, "'");
      } else {
        i++;
      }
    }
    return new Fonte(a, b);
  }

  /** Apaga em {@code b} o conteúdo do literal e devolve a posição depois do delimitador. */
  private static int pularLiteral(char[] b, String src, int ini, String fecha) {
    int n = src.length();
    int i = ini;
    while (i < n && !src.startsWith(fecha, i)) {
      if (src.charAt(i) == '\\' && i + 1 < n) {
        b[i++] = ' ';
      }
      if (src.charAt(i) != '\n') {
        b[i] = ' ';
      }
      i++;
    }
    return Math.min(n, i + fecha.length());
  }

  record Classe(String nome, int cabecalho, int corpoIni, int corpoFim, Classe pai) {
    String qualificado() {
      return pai == null ? nome : pai.qualificado() + "." + nome;
    }
  }

  static List<Classe> classes(char[] forma) {
    String f = new String(forma);
    List<int[]> brutas = new ArrayList<>();
    List<String> nomes = new ArrayList<>();
    Matcher m = DECL_CLASSE.matcher(f);
    while (m.find()) {
      int abre = f.indexOf('{', m.end());
      if (abre < 0) {
        continue;
      }
      brutas.add(new int[] {m.start(), abre, fechamento(f, abre)});
      nomes.add(m.group(2));
    }
    List<Classe> lista = new ArrayList<>();
    for (int k = 0; k < brutas.size(); k++) {
      int[] b = brutas.get(k);
      Classe pai = null;
      for (Classe c : lista) {
        if (c.corpoIni() < b[0] && b[0] < c.corpoFim()) {
          pai = c;
        }
      }
      lista.add(new Classe(nomes.get(k), b[0], b[1], b[2], pai));
    }
    return lista;
  }

  private static int fechamento(String f, int abre) {
    int prof = 0;
    for (int i = abre; i < f.length(); i++) {
      char c = f.charAt(i);
      if (c == '{') {
        prof++;
      } else if (c == '}' && --prof == 0) {
        return i;
      }
    }
    return f.length();
  }

  static List<Tag> extrairTags(String arquivo, String src) {
    Fonte fonte = limpar(src.replace("\r\n", "\n"));
    String forma = new String(fonte.forma());
    List<Classe> classes = classes(fonte.forma());
    List<Tag> tags = new ArrayList<>();
    Matcher m = TAG.matcher(forma);
    while (m.find()) {
      int ini = m.end();
      int fim = forma.indexOf('"', ini);
      if (fim < 0) {
        break;
      }
      String id = new String(fonte.visivel(), ini, fim - ini);
      tags.add(associar(arquivo, id, forma, fim + 1, classes));
    }
    return tags;
  }

  /** Descobre a declaração (classe ou método) a que a anotação pertence. */
  private static Tag associar(String arquivo, String id, String f, int pos, List<Classe> classes) {
    int i = pos;
    int n = f.length();
    while (i < n) {
      char c = f.charAt(i);
      if (Character.isWhitespace(c) || c == ')' || c == '}' || c == ',') {
        i++;
      } else if (c == '@') {
        i = pularAnotacao(f, i);
      } else {
        break;
      }
    }
    int fimCabecalho = i;
    while (fimCabecalho < n && "({;=".indexOf(f.charAt(fimCabecalho)) < 0) {
      fimCabecalho++;
    }
    String cabecalho = f.substring(i, Math.min(fimCabecalho, n));
    Matcher d = DECL_CLASSE.matcher(cabecalho);
    if (d.find()) {
      for (Classe c : classes) {
        if (c.cabecalho() == i + d.start()) {
          return new Tag(id, arquivo, c.qualificado(), "");
        }
      }
      return new Tag(id, arquivo, d.group(2), "");
    }
    Matcher nome = Pattern.compile("(\\w+)\\s*$").matcher(cabecalho);
    String metodo = nome.find() ? nome.group(1) : "?";
    Classe dona = null;
    for (Classe c : classes) {
      if (c.corpoIni() < i && i < c.corpoFim()) {
        dona = c;
      }
    }
    return new Tag(id, arquivo, dona == null ? "?" : dona.qualificado(), metodo);
  }

  private static int pularAnotacao(String f, int i) {
    int n = f.length();
    int k = i + 1;
    while (k < n && (Character.isJavaIdentifierPart(f.charAt(k)) || f.charAt(k) == '.')) {
      k++;
    }
    int j = k;
    while (j < n && Character.isWhitespace(f.charAt(j))) {
      j++;
    }
    if (j < n && f.charAt(j) == '(') {
      int prof = 0;
      for (; j < n; j++) {
        if (f.charAt(j) == '(') {
          prof++;
        } else if (f.charAt(j) == ')' && --prof == 0) {
          return j + 1;
        }
      }
      return n;
    }
    return k;
  }

  // ------------------------------------------------------------------ matriz

  record Analise(
      List<Requisito> requisitos,
      List<Tarefa> tarefas,
      Map<String, TreeSet<String>> testes,
      Path raiz) {

    Set<String> tarefasQueCobrem(String req) {
      return tarefas.stream()
          .filter(t -> t.covers().contains(req))
          .map(Tarefa::id)
          .collect(Collectors.toCollection(TreeSet::new));
    }

    /** Arquivos de evidência das tarefas que provam o requisito sem {@code @Tag}. */
    List<String> evidencias(String req) {
      return tarefas.stream()
          .filter(
              t ->
                  t.evidenceOnly().contains(req)
                      || (SEMPRE_EVIDENCIA.contains(req) && t.covers().contains(req)))
          .map(t -> "docs/specs/reviews/" + t.id() + ".evidence.md")
          .sorted()
          .toList();
    }

    boolean evidenciaPresente(String req) {
      return evidencias(req).stream().anyMatch(e -> Files.exists(raiz.resolve(e)));
    }

    boolean declaradoEvidenceOnly(String req) {
      return tarefas.stream().anyMatch(t -> t.evidenceOnly().contains(req));
    }

    int qtdTestes(String req) {
      return testes.getOrDefault(req, new TreeSet<>()).size();
    }

    boolean faltando(Requisito r) {
      return r.obrigatorioComTag()
          && qtdTestes(r.id()) == 0
          && !(declaradoEvidenceOnly(r.id()) && evidenciaPresente(r.id()));
    }

    List<Requisito> faltantes() {
      return requisitos.stream().filter(this::faltando).toList();
    }

    String prova(Requisito r) {
      boolean tag = qtdTestes(r.id()) > 0;
      boolean evid = !evidencias(r.id()).isEmpty();
      if (tag && evid) {
        return "@Tag + evidência";
      }
      if (tag) {
        return "@Tag";
      }
      if (evid) {
        return "evidência";
      }
      return r.obrigatorioComTag() ? "FALTA" : "só desenho";
    }

    String celulaTestes(Requisito r) {
      List<String> partes = new ArrayList<>();
      if (qtdTestes(r.id()) > 0 || r.obrigatorioComTag()) {
        partes.add(String.valueOf(qtdTestes(r.id())));
      }
      for (String e : evidencias(r.id())) {
        partes.add(e + (Files.exists(raiz.resolve(e)) ? "" : " (pendente)"));
      }
      return partes.isEmpty() ? "—" : String.join("; ", partes);
    }

    String matriz() {
      StringBuilder sb = new StringBuilder();
      sb.append("# Matriz de rastreabilidade\n\n");
      sb.append("Gerada por `scripts/check-traceability.sh --write`; não edite à mão. ");
      sb.append("Requisito → prova → tarefas que o cobrem (`covers`); o detalhe lista ");
      sb.append("`classe#método` (ou só a classe, quando a `@Tag` está nela) e o módulo.\n\n");
      sb.append("| Requisito | Prova | Testes | Tarefas |\n|---|---|---|---|\n");
      for (Requisito r : requisitos) {
        sb.append("| %s | %s | %s | %s |\n"
            .formatted(
                r.id(),
                prova(r),
                celulaTestes(r),
                String.join(", ", tarefasQueCobrem(r.id()))));
      }
      sb.append("\n## Detalhe dos testes\n");
      for (Requisito r : requisitos) {
        TreeSet<String> ts = testes.getOrDefault(r.id(), new TreeSet<>());
        if (ts.isEmpty()) {
          continue;
        }
        sb.append("\n### ").append(r.id()).append('\n');
        for (String t : ts) {
          sb.append("- ").append(t).append('\n');
        }
      }
      return sb.toString();
    }

    String relatorio() {
      StringBuilder sb = new StringBuilder();
      sb.append("Obrigatórios com @Tag sem prova:\n");
      List<Requisito> faltam = faltantes();
      if (faltam.isEmpty()) {
        sb.append("  (nenhum)\n");
      }
      for (Requisito r : faltam) {
        sb.append("  FALTA %s (tarefas: %s)\n"
            .formatted(r.id(), String.join(", ", tarefasQueCobrem(r.id()))));
      }
      sb.append("Provados por evidência:\n");
      boolean algum = false;
      for (Requisito r : requisitos) {
        for (String e : evidencias(r.id())) {
          algum = true;
          boolean existe = Files.exists(raiz.resolve(e));
          sb.append("  %s -> %s%s\n".formatted(r.id(), e, existe ? "" : " (pendente)"));
        }
      }
      if (!algum) {
        sb.append("  (nenhum)\n");
      }
      return sb.toString();
    }
  }

  static Analise analisar(Path raiz, List<String> arquivosDeTeste) throws IOException {
    List<Requisito> requisitos = lerRequisitos(raiz.resolve("docs/specs/02-requirements.md"));
    List<Tarefa> tarefas = lerTarefas(raiz.resolve("docs/specs/tasks"));
    Map<String, TreeSet<String>> testes = new TreeMap<>();
    for (String rel : arquivosDeTeste) {
      Path p = raiz.resolve(rel);
      if (!Files.isRegularFile(p)) {
        continue;
      }
      String modulo = rel.contains("/src/test/") ? rel.substring(0, rel.indexOf("/src/test/")) : ".";
      for (Tag t : extrairTags(rel, Files.readString(p))) {
        String item =
            t.metodo().isEmpty()
                ? "`%s` (%s)".formatted(t.classe(), modulo)
                : "`%s#%s` (%s)".formatted(t.classe(), t.metodo(), modulo);
        testes.computeIfAbsent(t.id(), k -> new TreeSet<>()).add(item);
      }
    }
    return new Analise(requisitos, tarefas, testes, raiz);
  }

  static List<Requisito> lerRequisitos(Path arquivo) throws IOException {
    Set<String> ids = new LinkedHashSet<>();
    Pattern titulo = Pattern.compile("^###\\s+((?:RNF|RF)-\\d+)\\b");
    Pattern linha = Pattern.compile("^\\|\\s*\\*\\*((?:RNF|RF)-\\d+)\\*\\*");
    for (String l : Files.readAllLines(arquivo, StandardCharsets.UTF_8)) {
      Matcher m = titulo.matcher(l);
      if (!m.find()) {
        m = linha.matcher(l);
        if (!m.find()) {
          continue;
        }
      }
      ids.add(m.group(1));
    }
    return ids.stream().map(Requisito::new).sorted().toList();
  }

  static List<Tarefa> lerTarefas(Path pasta) throws IOException {
    List<Tarefa> tarefas = new ArrayList<>();
    if (!Files.isDirectory(pasta)) {
      return tarefas;
    }
    try (var arquivos = Files.list(pasta)) {
      for (Path p :
          arquivos
              .filter(x -> x.getFileName().toString().matches("T-\\d+\\.md"))
              .sorted()
              .toList()) {
        List<String> fm = frontmatter(Files.readAllLines(p, StandardCharsets.UTF_8));
        String id = p.getFileName().toString().replace(".md", "");
        tarefas.add(new Tarefa(id, lista(fm, "covers"), lista(fm, "evidence_only")));
      }
    }
    return tarefas;
  }

  static List<String> frontmatter(List<String> linhas) {
    List<String> fm = new ArrayList<>();
    int tracos = 0;
    for (String l : linhas) {
      if (l.stripTrailing().equals("---")) {
        if (++tracos == 2) {
          break;
        }
        continue;
      }
      if (tracos == 1) {
        fm.add(l);
      }
    }
    return fm;
  }

  static Set<String> lista(List<String> fm, String chave) {
    Set<String> itens = new LinkedHashSet<>();
    boolean emLista = false;
    for (String bruta : fm) {
      String l = bruta.replaceFirst("\\s+#.*$", "").stripTrailing();
      if (l.startsWith(chave + ":")) {
        String resto = l.substring(chave.length() + 1).strip();
        emLista = resto.isEmpty() || resto.startsWith("#");
        if (resto.startsWith("[")) {
          String miolo = resto.replaceAll("^\\[|\\]$", "");
          Arrays.stream(miolo.split(","))
              .map(String::strip)
              .filter(s -> !s.isEmpty())
              .forEach(itens::add);
        }
      } else if (emLista) {
        String item = l.strip();
        if (item.startsWith("- ")) {
          itens.add(item.substring(2).strip());
        } else if (!l.isEmpty() && !Character.isWhitespace(l.charAt(0))) {
          emLista = false;
        }
      }
    }
    return itens;
  }
}
