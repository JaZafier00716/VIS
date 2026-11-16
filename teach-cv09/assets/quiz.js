// Reusable quiz widgets.
// Multiple choice: <div class="quiz" data-answer="b" data-why="explanation"> <p class="q">…</p>
//   <label><input type="radio" name="q1" value="a"> …</label> … <div class="fb"></div></div>
// Ordering: <div class="quiz order" data-order="x,y,z"> <ol><li><select>…</select></li>…</ol>
//   <button>Check</button><div class="fb"></div></div>
document.querySelectorAll('.quiz:not(.order)').forEach(q => {
  const fb = q.querySelector('.fb');
  q.querySelectorAll('input[type=radio]').forEach(r => r.addEventListener('change', () => {
    const ok = r.value === q.dataset.answer;
    fb.className = 'fb ' + (ok ? 'ok' : 'bad');
    fb.textContent = ok ? '✓ Right. ' + (q.dataset.why || '') : '✗ Not quite – try again before reading on.';
  }));
});
document.querySelectorAll('.quiz.order').forEach(q => {
  const want = q.dataset.order.split(',');
  const fb = q.querySelector('.fb');
  q.querySelector('button').addEventListener('click', () => {
    const got = [...q.querySelectorAll('select')].map(s => s.value);
    const wrong = got.map((v, i) => v === want[i] ? null : i + 1).filter(Boolean);
    fb.className = 'fb ' + (wrong.length ? 'bad' : 'ok');
    fb.textContent = wrong.length ? '✗ Steps ' + wrong.join(', ') + ' are in the wrong place.'
      : '✓ That is the exact call order in OrderService.changeStatus. ' + (q.dataset.why || '');
  });
});
